# XTA-AdKiller

超级课程表（com.xtuone.android.syllabus）去广告 LSPosed 模块。

## 功能
- **开屏广告**：拦截 AMPS 聚合开屏请求并伪造无广告回调，秒进主页
- **首页横幅**：拦截广告位配置下发 + Fresco 图片加载 + 隐藏横幅容器
- **课表页宝箱浮窗**：隐藏皮肤广告入口
- **全类型覆盖**：AMPS 七大广告 API（开屏/横幅/信息流/插屏/原生/激励视频/统一原生）+ 四家瀑布流适配器（Noah/ssp/聚量/Merak）

## 安装
1. 设备需要 KernelSU/Magisk + Zygisk + LSPosed（API 101+，支持 Android 9~16）
2. 安装 [最新 Release APK](../../releases/latest)
3. 在 LSPosed Manager 中启用模块，勾选作用域「超级课程表」
4. 强停超级课程表后重新打开即可

## 构建
```
cmd /c build.cmd
```
纯 javac + d8 + aapt2 + zipalign + apksigner，无需 Gradle。依赖 Android SDK build-tools 36.1.0 与 platform android-34。

## 技术
基于 Modern Xposed API（io.github.libxposed:api:102）。目标 App 使用 WS-Sec 壳，业务代码运行时解密；
模块通过轮询 loadClass 等待解密后按运行时验证的方法签名安装钩子，竞速窗口 < 1s。
详细杀点与坑位记录见源码注释。

## 免责声明
仅供学习与个人使用，请于下载后 24 小时内自行斟酌删除；不得用于商业用途。

## License
MIT
