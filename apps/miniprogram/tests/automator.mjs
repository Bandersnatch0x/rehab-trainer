// 微信开发者工具自动化：页面级断言。
// 前置：IDE 已用 `cli auto --project <path> --auto-port 9420` 启动，且 9420 可连。
import automator from 'miniprogram-automator';

const WS = process.env.WX_AUTO_WS || 'ws://127.0.0.1:9420';

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
}

const miniProgram = await automator.connect({ wsEndpoint: WS });

try {
  // 1. 首页：应渲染 Q 版标题与云端异步提示，且不出现临床字段（D20）
  const home = await miniProgram.reLaunch('/pages/index/index');
  await home.waitFor(1500);
  const homeText = await (await home.$('.page')).text();
  check('首页渲染 Q 版标题', homeText.includes('今天也一起练习'), homeText.slice(0, 40));
  check('首页保留无实时反馈提示', homeText.includes('云端异步'), '');
  check('首页无 H-Y / 症状等临床词', !/H-Y|hyStage|症状|跌倒风险/.test(homeText), '');

  // 2. 未登录时应有明确提示，而不是空白
  check(
    '未登录显示绑定提示',
    /尚未登录|绑定/.test(homeText),
    homeText.slice(0, 80),
  );

  // 3. 五个业务页面都能打开且不报错
  for (const page of [
    '/pages/patient/index',
    '/pages/records/index',
    '/pages/alerts/index',
    '/pages/assessment/index',
    '/pages/training/index',
  ]) {
    const current = await miniProgram.reLaunch(page);
    await current.waitFor(1200);
    const path = current.path;
    const text = await (await current.$('.page')).text();
    check(`${page} 可打开`, path === page.replace(/^\//, ''), path);
    check(`${page} 有可见内容`, text.trim().length > 0, `${text.trim().length} 字符`);
    check(`${page} 无临床禁用词`, !/H-Y|hyStage|症状|跌倒风险/.test(text), '');
  }

  // 4. 训练页必须显式标注演示数据
  const training = await miniProgram.reLaunch('/pages/training/index');
  await training.waitFor(1200);
  const trainingText = await (await training.$('.page')).text();
  check('训练页标注演示数据', trainingText.includes('演示数据'), '');
} finally {
  await miniProgram.disconnect();
}

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length === 0 ? 0 : 1);
