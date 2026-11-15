# Tank Factory（个人二改分支）

> 本仓库是 [Domity/TankFactory](https://github.com/Domity/TankFactory) 的个人 fork，
> 在上游基础上新增了 **光棱坦克（Prism Tank）** 的制作与显形。
> 上游功能、代码与署名全部保留；二改内容在分支
> [`feature/prism-tank`](https://github.com/polarlights0/TankFactory/tree/feature/prism-tank)，
> 改动说明见 [PR #1](https://github.com/polarlights0/TankFactory/pull/1)。

## 本仓库新增：光棱坦克（Prism Tank）

算法参考 [Uyanide/Mirage_Decode](https://github.com/Uyanide/Mirage_Decode)
（原 `TankFactory/Mirage_Decode` 已废弃，只剩跳转页），界面沿用项目现有的 Cyber 主题组件。

**是什么**：一种「表图 + 里图」合成图。整张图看起来是表图，把亮度落在某个窄区间的像素
拉回全色阶后，里图就会显形。制作入口在主页「制作光棱坦克」，显形入口是「查看光棱坦克」。

### 制作
- 里图色阶端点 / 表图色阶端点：越低越隐蔽，表图端点越高显形效果越好（必须 里 < 表）
- 里图对比度 / 表图对比度：各自独立调整
- 里图取灰度 / 表图取灰度：舍弃颜色，显著提升抗压缩能力
- 花纹斜率（0-4）、间距（1-4）、方向（行/列）、反向（表暗里亮）
- 最大长边：里图等比缩放，表图居中裁剪到里图尺寸
- 实时预览，可保存 PNG / WEBP

### 显形
- 上下限阈值、对比度
- 6 种区间外填充：填充黑 / 填充白 / 透明 / 复制左 / 复制上 / 平滑填充
- 平滑填充使用 24 邻域高斯权重迭代，大图会自动限制总迭代量

### 实现
| 文件 | 作用 |
| --- | --- |
| `app/src/main/cpp/prism_algo.h` | 纯 C++ 算法（编码 / 解码 / 迭代填充），不依赖 JNI |
| `app/src/main/cpp/prism_tank_encoder.cpp` | JNI 编解码，含位图格式校验与异常兜底 |
| `.../prismtank/PrismTankCoder.kt` | JNI 桥；`loadLibrary` 失败改为返错提示，不再闪退 |
| `.../prismtank/PrismTankMaker*` | 制作页（ViewModel + Compose） |
| `.../prismtank/PrismTankViewer*` | 显形页（ViewModel + Compose） |
| `MainActivity.kt` / `strings.xml` | 主菜单入口、路由、文案 |

### 自测结论
- 与参考实现逐像素对比：6 组参数 × 6 种填充 × 2 档对比度，编解码输出**字节一致**
- 真实图片往返（480×480 → JPEG q90 → 解码）：解码图与里图亮度相关系数 0.98，缩略图与表图 0.9956
- 极端尺寸（1×1、1×255、255×1）+ 哨兵 + UBSAN：无越界、无 UB
- JNI 入口直测（假 Bitmap 层）：编码成功，6 种填充均能解出里图
- Kotlin/Compose 全模块编译通过；NDK 28.2 交叉编译 arm64 链接成功；`assembleDebug` 出包可安装

### 已知限制
- 里图被压进很窄的色阶区间，经过 JPEG 或社交平台二次压缩后会掉效果，建议保存 PNG
- 显形效果依赖阈值，拿不到里图时先试着调上下限阈值 / 对比度 / 填充方式

## 构建
```bash
./gradlew assembleDebug     # 需要 JDK 17+、Android SDK 36、NDK 28.2.13676358
```
release 版本默认只打包 arm64-v8a 库。

## 致谢
- 上游应用：[Domity/TankFactory](https://github.com/Domity/TankFactory)
- 幻影坦克参考：[TankFactory/Mirage_Colored](https://github.com/TankFactory/Mirage_Colored)、[fEndman/WarFactory](https://github.com/fEndman/WarFactory)
- 光棱坦克参考：[Uyanide/Mirage_Decode](https://github.com/Uyanide/Mirage_Decode)

许可证与上游一致，见 [LICENSE](LICENSE)。

---

# 以下为上游原始说明

# Tank Factory
## 没归档就是还在，不干了自会归档
## 基本信息
- 参考自[战车工厂](https://github.com/fEndman/WarFactory)，[幻影坦克工厂](https://github.com/TankFactory/Mirage_Colored)
- **本应用仅供交流学习，禁止用于违法用途。用户因不当使用本软件所造成的一切后果由用户自行承担责任。**
- release版本默认只打包arm64-v8a库
## 功能&计划
- [x] 幻影/无影坦克查看和修改
- [x] 图片加解密
- [ ] 更多图片功能
- [ ] 跑路
## 下载
  最新稳定版本可在[这里](https://github.com/Domity/TankFactory/releases/latest)找到  
  也可在[release页面](https://github.com/Domity/TankFactory/releases)下载测试版本第一时间尝试新功能  
  请仅从github release下载本应用。对于从其他渠道下载的应用，开发者不保证它们的安全性
### 题外话
  Tank Factory最开始只是作为幻影坦克查看器开发的，并且叫 Tank Viewer
