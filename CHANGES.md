## 更新日志

### 1.1.2

- **新增：** 横屏视角添加SK按钮，可用于Dolphin的输入配置
- **新增：** 关于页面对UDPWiiHook的描述添加下载链接

### 1.1.1

- **新增：** 新增了3款虚拟陀螺仪方案，可以在连接服务器界面手动切换
	- 所有方案（按默认采用顺序排序）：陀螺仪、旋转矢量、朝向传感器、重力+磁场、加速度+磁场
- **新增：** 关于页面新增对UDPWiiHook的描述
- **更改：** 生成使用的Java版本指定（升级）为Java 1.8
	- 这一更改不影响兼容性

### 1.1.0

- 为与UDPWiiHook配合而进行的改动
- **新增：** 采集的数据中现在包括加速度时间戳及陀螺仪读数
	- 对于不支持陀螺仪的设备，将使用加速度计和磁场传感器的读数进行模拟
- **更改：** 调整了横屏模式功能按键的布局以与DualShock系列手柄相匹配 - 见下表
- **更改：** 触摸数据现在仅在用户进行触摸操作时发送
- **更改：** 对Release版本的生成启用了代码缩减和优化
- **更改：** 应用在后台时将不再工作，以减少电量使用
- **修复：** 模拟LED区域不再受服务器名称中的数字影响
- ~~**更改：** 触控板的有效区域缩减为布局大小，同时导致触控灵敏度提高（？）~~

| 横屏视角 | 上 | 下 | 左 | 右
| - | - | - | - | -
| 更改前 | B | 2 | 1 | A
| 更改后 | 1 | B | 2 | A

| 竖屏视角 | 上 | 下 | 左 | 右
| - | - | - | - | -
| 更改前 | 1 | A | 2 | B
| 更改后 | 2 | A | B | 1

### 1.0.4

- **更改：** 修改了体感灵敏度各选项的数值以匹配Dolphindroid的行为 - 见下表
- **更改：** 竖屏模式摇杆的工作模式由8向改为4向
- **更改：** 横屏模式的体感灵敏度由1.2x（M2）改为1.0x（M0）
- **更改：** 横屏模式右摇杆（不可见）的工作模式由8向改为4向
- **修复：** 体感灵敏度选项现在可以正常工作
- ~~**新增：** 恢复了横屏模式右摇杆切换按钮的显示（默认关闭）~~

| 选项 | 1 | 2 | 3 | 4（不可见）
| - | - | - | - | -
| 更改前 | M1（1.12x） | M2（1.17x） | M3（默认，1.22x） | MAX（1.27x）
| 更改后 | M0（默认，1.0x） | M1（1.1x） | M2（1.2x） | MAX（1.3x）

### 1.0.3

- **新增：** 点击触控板现在视为按下A键
- **移除：** 竖屏模式下双击摇杆不再视为按下A键，同时移除了相关的振动功能及权限请求
- **移除：** 移除了隐藏的`AlternateActivity`
- **更改：** 横屏模式摇杆的默认工作模式由4向改为8向
- **修复：** 在屏幕较小的设备上，体感灵敏度选项不再与《舞力全开》兼容选项重叠
- **修复：** 摇杆不再出现偶发的卡键问题

### 1.0.2

- 接管开发 - 代码库基于v1.0.1的反编译结果
- **新增：** 竖屏模式下点击LED区域现在可以显示/隐藏按钮和触控板
- **新增：** 新增简体中文语言支持
- **更改：** 关于页面链接版本更新为目前的最新版本
- **更改：** 细微的布局变更

### 1.0.1

- **新增：** 新增《舞力全开》兼容模式（默认关闭）
- **新增：** 新增指示玩家位次的LED模拟区域
- **移除：** 隐藏了体感灵敏度的MAX模式

### 1.0.0

- 初始版本

