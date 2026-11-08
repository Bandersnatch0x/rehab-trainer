#!/usr/bin/env bash
#
# 备份恢复演练：pg_dump → 新库恢复 → 逐项对比 → 清理。
#
# 为什么要有这个脚本：`pg_dump -Fc` 能跑通不等于备份可用。真正要证明的是「恢复出来的库
# 和源库结构一致、行数一致、迁移版本一致」。这一步此前只在 0001 时点人工做过一次，
# 之后新增了 0002–0005，脚本把它变成随时可复跑的动作。
#
# 跑在装有 PostgreSQL 客户端工具的主机上（api 容器或 postgres 容器内亦可，镜像自带 pg_dump）：
#
#   PGHOST=127.0.0.1 PGPORT=55432 PGUSER=rehab PGPASSWORD=… SOURCE_DB=rehab \
#       bash deploy/verify_backup_restore.sh
#
# 只读源库；恢复库默认名 ${SOURCE_DB}_restore_$$，无论成败都会删掉。
# 应在停止写入时执行：源库在转储后若继续写入，行数对比会如实报出差异（这是预期行为，不是误报）。

set -euo pipefail

: "${PGUSER:?需要 PGUSER}"
: "${PGPASSWORD:?需要 PGPASSWORD}"
: "${SOURCE_DB:?需要 SOURCE_DB}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
export PGHOST PGPORT PGUSER PGPASSWORD

RESTORE_DB="${RESTORE_DB:-${SOURCE_DB}_restore_$$}"
DUMP="${DUMP:-${TMPDIR:-/tmp}/${SOURCE_DB}.dump}"
ADMIN_DB="${ADMIN_DB:-postgres}"

for tool in psql pg_dump pg_restore createdb dropdb; do
    command -v "$tool" >/dev/null || { echo "缺少 $tool，请在装有 PostgreSQL 客户端的主机上运行" >&2; exit 127; }
done

restore_db_pattern='^[A-Za-z0-9_]+$'
[[ "$SOURCE_DB" =~ $restore_db_pattern ]] || { echo "SOURCE_DB 只允许字母数字与下划线" >&2; exit 2; }
[[ "$RESTORE_DB" =~ $restore_db_pattern ]] || { echo "RESTORE_DB 只允许字母数字与下划线" >&2; exit 2; }
[[ "$RESTORE_DB" != "$SOURCE_DB" ]] || { echo "恢复库不能与源库同名" >&2; exit 2; }

# 恢复库必须是本次新建的：若已存在（上一次演练残留或手工 DR 副本），
# 后面的 dropdb 与 cleanup trap 会把它删掉。必须在安装 trap 之前拒绝。
if psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$RESTORE_DB'" -d "$ADMIN_DB" | grep -qx 1; then
    echo "恢复库 ${RESTORE_DB} 已存在，拒绝覆盖" >&2
    exit 2
fi

cleanup() {
    dropdb --if-exists "$RESTORE_DB" >/dev/null 2>&1 || true
    rm -f "$DUMP"
}
trap cleanup EXIT

echo "源库 ${SOURCE_DB}@${PGHOST}:${PGPORT} → 恢复库 ${RESTORE_DB}"

# 1) 转储并恢复（--exit-on-error：任何一条语句失败都必须让演练失败，不能"尽力恢复"）。
pg_dump -Fc -d "$SOURCE_DB" -f "$DUMP"
echo "PASS pg_dump：$(wc -c <"$DUMP") 字节"
dropdb --if-exists "$RESTORE_DB" >/dev/null
createdb "$RESTORE_DB"
pg_restore --exit-on-error -d "$RESTORE_DB" "$DUMP"
echo "PASS pg_restore --exit-on-error"

# 2) 结构与数据对比。
failures=0

compare_scalar() {
    local label="$1" query="$2"
    local source_value restore_value
    source_value="$(psql -tAc "$query" -d "$SOURCE_DB")"
    restore_value="$(psql -tAc "$query" -d "$RESTORE_DB")"
    if [[ "$source_value" == "$restore_value" ]]; then
        echo "PASS ${label}：${source_value}"
    else
        echo "FAIL ${label}：源 ${source_value} ≠ 恢复 ${restore_value}" >&2
        failures=$((failures + 1))
    fi
}

compare_scalar "迁移版本" "SELECT version_num FROM alembic_version"
compare_scalar "表数量" "SELECT count(*) FROM pg_tables WHERE schemaname='public'"
compare_scalar "检查约束数量" \
    "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND c.contype='c'"
compare_scalar "索引数量" \
    "SELECT count(DISTINCT indexname) FROM pg_indexes WHERE schemaname='public'"

# 3) 逐表行数与内容摘要。
#    行数一致但内容被改坏（尤其是 JSON 列）同样是"恢复成功"的假象，
#    因此再按行文本排序取一次 md5——覆盖所有列，JSON 损坏时这个值会变。
#    摘要要对整表排序，数据量大时较慢；演练库通常很小。
tables="$(psql -tAc "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY 1" -d "$SOURCE_DB")"
for table in $tables; do
    compare_scalar "行数 ${table}" "SELECT count(*) FROM \"${table}\""
    compare_scalar "内容摘要 ${table}" \
        "SELECT md5(coalesce(string_agg(t::text, '' ORDER BY t::text), '')) FROM \"${table}\" t"
done

if (( failures > 0 )); then
    echo "FAIL：${failures} 项不一致，备份不可用或源库在演练期间被写入" >&2
    exit 1
fi

echo "PASS：$(wc -l <<<"$tables") 张表结构与行数一致，alembic_version 一致；恢复库已清理"
