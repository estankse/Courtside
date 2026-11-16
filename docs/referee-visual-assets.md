# 裁判端视觉素材

裁判端按页面用途分配摄影，使用深色渐变、荧光绿、蓝红细线和手写姓名保持统一的运动风格。当前共 21 张本机摄影资源，其中 18 张来自新增的 Commons 摄影，WebP 文件合计 1,337,028 字节（约 1.28 MiB）。页面主视觉保持固定取景，比赛卡片从专用的 8 张背景池中按比赛 ID 随机分配；同一比赛在滚动、筛选、刷新、切换页面及重启后保持同图。所有照片随安装包提供，离线可用。

## 页面搭配

| 页面 | 摄影与设计 |
| --- | --- |
| 登录 | 沿用林丹、李宗伟经典对阵照片，只在登录使用 |
| 工作台 | 林丹得分庆祝，突出上场气氛 |
| 我的赛程 | 谌龙备战的近景标题背景；比赛卡片从独立摄影池中随机选图 |
| 执裁记录 | 安赛龙侧身备战，与赛程标题区区分 |
| 比赛详情 | 林丹与李宗伟的备战照片，蓝红双栏与手写姓名 |
| 入场动画 | 林丹挥拍、李宗伟准备扣杀，使用区别于详情页的动作照片 |
| 正式计分 | 原有林丹、李宗伟摄影专用于蓝红计分卡；明暗模式均保持数字可读 |
| 中场间歇 | 球场全景，突出赛场与荧光倒计时 |
| 局间休息 | 傅海峰跃起扣杀，与中场间歇区分 |
| 赛后回顾 | 蔡赟、傅海峰赛场庆祝，低对比度衬托实际获胜者和比分 |

摄影为装饰层，不承载点击手势或实际参赛者身份。导航、任务处理、计分规则、离线存储及按钮操作区域延续已有实现。详情页的主要操作固定在底部，提示消息位于操作区上方。

## 新增素材与授权

新增摄影来自 Wikimedia Commons 各文件页。下表及安装包中的 `client/src/main/assets/referee_photo_credits.json` 保留作者、来源、授权链接和处理说明。裁剪和压缩后的照片沿用对应授权；在界面中叠加渐变与线条，没有生成、替换或重绘运动员形象。裁判端可通过“账户与连接 → 图片来源与授权”离线查看清单，按需打开原图与授权网页。

| 资源 / 用途 | 作者 | 授权 | 原图来源 |
| --- | --- | --- | --- |
| `referee_workbench_victory.webp`<br>工作台 · 林丹得分庆祝 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Point Won (7758865282).jpg](https://commons.wikimedia.org/wiki/File:Point_Won_(7758865282).jpg) |
| `referee_schedule_chen_long.webp`<br>我的赛程 · 谌龙备战 | Ian Patterson | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) | [Chen Long2012.jpg](https://commons.wikimedia.org/wiki/File:Chen_Long2012.jpg) |
| `referee_records_axelsen.webp`<br>执裁记录 · 安赛龙 | Fauziananta | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) | [Viktor Axelsen - Indonesia Masters 2018.jpg](https://commons.wikimedia.org/wiki/File:Viktor_Axelsen_-_Indonesia_Masters_2018.jpg) |
| `referee_detail_lin_dan.webp`<br>比赛详情 · 林丹 | Ian Patterson | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) | [Lin Dan 9055.jpg](https://commons.wikimedia.org/wiki/File:Lin_Dan_9055.jpg) |
| `referee_detail_lee.webp`<br>比赛详情 · 李宗伟 | Pierre-Yves Beaudouin | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | [Yonex IFB 2013 - Quarterfinal - Lee Chong Wei vs Boonsak Ponsana 05.jpg](https://commons.wikimedia.org/wiki/File:Yonex_IFB_2013_-_Quarterfinal_-_Lee_Chong_Wei_vs_Boonsak_Ponsana_05.jpg) |
| `referee_entrance_lin_dan.webp`<br>选手入场 · 林丹挥拍 | Ian Patterson | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) | [Lin Dan 9020.jpg](https://commons.wikimedia.org/wiki/File:Lin_Dan_9020.jpg) |
| `referee_entrance_lee.webp`<br>选手入场 · 李宗伟扣杀准备 | Antony Stanley | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Lee Chong Wei Prepares To Smash cropped.jpg](https://commons.wikimedia.org/wiki/File:Lee_Chong_Wei_Prepares_To_Smash_cropped.jpg) |
| `referee_interval_arena.webp`<br>中场间歇 · 奥运球场全景 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Mens Singles Badminton Final, London 2012 (7758890090).jpg](https://commons.wikimedia.org/wiki/File:Mens_Singles_Badminton_Final,_London_2012_(7758890090).jpg) |
| `referee_game_break_fu.webp`<br>局间休息 · 傅海峰跃起扣杀 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Fu Haifeng, Mens Doubles Badminton Final (8172656810).jpg](https://commons.wikimedia.org/wiki/File:Fu_Haifeng,_Mens_Doubles_Badminton_Final_(8172656810).jpg) |
| `referee_recap_doubles.webp`<br>赛后回顾 · 蔡赟傅海峰赛场庆祝 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Cai Yun and Fu Haifeng Win For China! (8172646238).jpg](https://commons.wikimedia.org/wiki/File:Cai_Yun_and_Fu_Haifeng_Win_For_China!_(8172646238).jpg) |

## 沿用的用户素材

| 资源 | 工作区原文件 | 当前用途 |
| --- | --- | --- |
| `referee_rivals.webp` | `Android App/myapp/BadmintonCounter/app/src/main/res/drawable/begin.jpg` | 登录主视觉 |
| `referee_lin_dan.webp` | `Android App/myapp/BadmintonCounter/app/src/main/res/drawable/lindan15.png` | 蓝方计分卡片 |
| `referee_lee_chong_wei.webp` | `Android App/myapp/BadmintonCounter/app/src/main/res/drawable/leecw1.jpg` | 红方计分卡片 |

图片统一放在 `client/src/main/res/drawable-nodpi`，由裁判端组件引用。原始下载、候选图、取景比较、构建日志和运行截图均存放在工作区 `tmp/referee_gallery_20261110`。

## 显示与交互

- 渐变优先保证姓名、比分、时间和操作按钮的对比度；浅色计分模式进一步淡化摄影。
- 横屏计分沿用大号分数与 48dp 工具条，照片不挤占计分区域；交换显示时背景跟随蓝红一侧。
- 横屏入场左右对阵，红方姓名靠右，为中间 VS 留出空间；竖屏上下对阵。完整入场约 3.4 秒，继续比赛约 1.4 秒，可跳过，遵循系统关闭动画的设置。
- 中场间歇和局间休息仍按真实结束时间倒计时，支持到时自动恢复与提前结束。

## 比赛卡片背景（新增 8 张）

卡片沿用原有高度、信息层级、蓝红顶线与整卡点击区域。摄影仅在裁判端的待办、赛程和执裁记录卡片中启用，赛事组端不启用。以比赛 ID 为种子选择背景，不依赖列表位置或比分、状态，不在重组时重新抽取；随机选择允许不同比赛使用同一张照片。

原图针对约 1.92:1 的小卡片单独裁剪，并逐图设置顶部、居中或底部取景，使横屏宽卡片尽量保留运动员面部与挥拍动作。图片以 30% 透明度绘制，姓名两侧再叠加深色遮罩，底部时间与场地区域渐变接近不透明，防止背景抢占信息。照片是羽毛球主题装饰，不代表实际参赛者。新增 8 张 WebP 合计 388,866 字节（约 0.37 MiB）。

| 资源 / 用途 | 作者 | 授权 | 原图来源 |
| --- | --- | --- | --- |
| `referee_card_lin_return.webp`<br>赛程卡片 · 林丹救球 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Lin Dan, China (7758874382).jpg](https://commons.wikimedia.org/wiki/File:Lin_Dan,_China_(7758874382).jpg) |
| `referee_card_lee_airborne.webp`<br>赛程卡片 · 李宗伟跃起扣杀 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Plenty Of Air (7758869078).jpg](https://commons.wikimedia.org/wiki/File:Plenty_Of_Air_(7758869078).jpg) |
| `referee_card_ginting.webp`<br>赛程卡片 · 金廷迎球 | Fauziananta | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) | [Anthony Sinisuka Ginting - Indonesia Masters 2018.jpg](https://commons.wikimedia.org/wiki/File:Anthony_Sinisuka_Ginting_-_Indonesia_Masters_2018.jpg) |
| `referee_card_momota.webp`<br>赛程卡片 · 桃田贤斗备战 | Nardisoero | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | [Kento Momota (JPN).jpg](https://commons.wikimedia.org/wiki/File:Kento_Momota_(JPN).jpg) |
| `referee_card_intanon.webp`<br>赛程卡片 · 因达农备战 | Nardisoero | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | [Ratchanok Intanon (THA).jpg](https://commons.wikimedia.org/wiki/File:Ratchanok_Intanon_(THA).jpg) |
| `referee_card_shuttle.webp`<br>赛程卡片 · 羽毛球场边特写 | Syed Ahmad Fathi | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | [Shuttlecock on a badminton court.jpg](https://commons.wikimedia.org/wiki/File:Shuttlecock_on_a_badminton_court.jpg) |
| `referee_card_doubles.webp`<br>赛程卡片 · 蔡赟傅海峰双打对局 | Antony Stanley from Gloucester, UK | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) | [Cai Yun and Fu Haifeng vs. Mathias Boe and Carsten Mogensen In The Mens Doubles Badminton Final (8172623247).jpg](https://commons.wikimedia.org/wiki/File:Cai_Yun_and_Fu_Haifeng_vs._Mathias_Boe_and_Carsten_Mogensen_In_The_Mens_Doubles_Badminton_Final_(8172623247).jpg) |
| `referee_card_nishimoto.webp`<br>赛程卡片 · 西本拳太网前备战 | Nardisoero | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) | [Kenta Nishimoto.jpg](https://commons.wikimedia.org/wiki/File:Kenta_Nishimoto.jpg) |

本次候选图、下载元数据、裁剪清单、构建日志与原生界面验收截图位于 `tmp/referee_cards_20261110`；安装包内的图片授权清单已同步增加这 8 项，可在“账户与连接 → 图片来源与授权”查看。
