# UDPMote

*Allows Android devices to be used as Wiimotes via UDPWii protocol.*

This is a fork of [Anthony Calosa's UDPMote][udpmote], aimed to provide a better experience for my family.

Since no source code is available despite the author's promise to release the source "once he cleaned them", this fork is based on a decompiled codebase of UDPMote v1.0.1. (To Anthony Calosa: contact me if this is a copyright violation.) Major improvements are as follows:

- Merged functionalities of UDPMote, [Dolphindroid][dolphindroid] and ["Dolphindroid JDver"][jdver] into one app
- MotionPlus emulation support (req. [UDPWiiHook][udpwiihook])
- Virtual gyroscope support for phones with no physical gyroscope
- Simplified Chinese support
- Less bugs & less input stuttering

Read [CHANGES.md](CHANGES.md) for all modifications (in Chinese).

---

*UDPMote: Wii模拟器体感遥控器*

这是[Anthony Calosa的原版UDPMote][udpmote]的fork，开发目标是优化用户体验。

原作者未公开UDPMote的源代码（说是“代码写得太乱整理一下就发出来”结果十多年了都没动静），所以本fork基于对UDPMote v1.0.1的反编译结果（侵权删除）。进行的主要改进如下：

- 集成了UDPMote、[Dolphindroid][dolphindroid]和[所谓“Dolphindroid JDver”][jdver]的所有功能
- 支持动感强化器模拟（需要与[UDPWiiHook][udpwiihook]配合使用）
- 对于没有内置陀螺仪的设备可以模拟陀螺仪
- 支持简体中文
- 修复各类BUG，体验更流畅

完整的修改列表参见[CHANGES.md](CHANGES.md)。

[udpmote]: https://forums.dolphin-emu.org/Thread-unofficial-udpmote-for-android
[dolphindroid]: https://github.com/gridranger/dolphindroid
[jdver]: https://tieba.baidu.com/p/5024841896
[udpwiihook]: https://github.com/EZForever/UDPWiiHook

