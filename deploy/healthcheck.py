"""容器健康检查：探应用的 /readyz。

就绪判定（含数据库可达）交给应用自己做，探针只读结果。这样测的是**真正在服务流量的
那个进程与它的连接池**；此前这里是另起一个引擎直连数据库，池耗尽时它会报"健康"，
而实际上所有业务请求都在等连接。
"""

import sys
import urllib.error
import urllib.request

READY_URL = "http://127.0.0.1:8000/readyz"
TIMEOUT_SECONDS = 3


def probe(url: str = READY_URL, timeout: float = TIMEOUT_SECONDS) -> int:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            return 0 if response.status == 200 else 1
    except urllib.error.HTTPError as error:
        # 503 是就绪失败，属预期内的否定结果，不是脚本异常。
        print(f"readyz 就绪失败：HTTP {error.code}", file=sys.stderr)
        return 1
    except OSError as error:
        print(f"readyz 不可达：{error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(probe())
