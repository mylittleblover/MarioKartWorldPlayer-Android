# AND-21 · 后台播放与媒体通知

- **checked_at**: 2026-11-08T15:40+08:00 (Asia/Shanghai)
- **证据级别**: **设备实测**（58 个设备测试全过，含 6 个后台播放测试）

---

## 1. 问题

之前引擎住在 `ViewModel` 里，**退出界面播放就停**。这不是播放器的行为。

---

## 2. 架构调整：所有权从 ViewModel 移到进程

```
之前：
  PlayerViewModel ── 拥有 ──> AudioEngine / AudioOutput / PlaybackController
  界面销毁 → 播放停止

之后：
  PlaybackHolder（进程级单例）
        ├── PlaybackService  ── 前台服务 + MediaSession + 通知
        └── PlayerViewModel  ── 只读状态、发命令
  界面销毁 → 播放继续
```

**前台服务无法拥有 lifecycle 作用域的对象**，所以所有权必须上移。

---

## 3. `PlaybackSession`：可测试的传输层

`PlaybackSession` 是**普通类 + 注入的引擎工厂**，不是单例 object：

```kotlin
PlaybackSession(
    createEngine = { AudioEngine.create() },
    openOutput = { handle, rate -> AudioOutput.open(handle, rate) },
)
```

**理由**：传输规则与界面是否可见无关，所以应该能在没有 Android、没有设备的情况下测试。

---

## 4. 修掉一个会**杀死进程**的真 bug

首次实现时，用空闲会话启动服务直接崩溃：

```
FATAL EXCEPTION: main
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
  Context.startForegroundService() did not then call Service.startForeground()
```

Logcat 里的关键一行：

```
Bringing down service while still waiting for start foreground
```

### 根因

`startForegroundService()` 向系统**承诺**随后会调用 `startForeground()`。

我的代码在 `onStartCommand` 里确实先调了 `startForeground`，但紧接着 `refresh()` 发现会话空闲 → `stopForeground(REMOVE)` + `stopSelf()`。

**服务在系统完成前台登记之前就被拆掉了**，于是系统判定「承诺未兑现」并杀进程。

### 修法

不是在 `onStartCommand` 里补救，而是**根本不为空闲会话启动前台服务**：

```kotlin
fun start(context: Context): Boolean {
    if (!PlaybackHolder.get().state.value.active) return false
    ...
}
```

**这是正确的语义**：没有东西在播，就不该占用前台服务。

---

## 5. 第二个 bug：通知不会消失

`stopForeground(STOP_FOREGROUND_DETACH)` 只是**分离**，不移除通知。

结果：停止后通知栏残留一个媒体控件，**后面什么都没有**。

改成：

```kotlin
stopForeground(STOP_FOREGROUND_REMOVE)
manager.cancel(NOTIFICATION_ID)
```

并在 `onDestroy` 里也 `cancel`。

---

## 6. 六个设备测试

| 测试 | 断言 |
|---|---|
| `theServiceIsDeclaredAsMediaPlayback` | 必须声明 `mediaPlayback` 前台类型（Android 14+ 否则拒绝） |
| `theServiceIsNotExported` | 服务不可导出 |
| `aPlayingSessionProducesAForegroundNotification` | **通知出现，且标题 == 曲目名** |
| `stoppingRemovesTheNotification` | 停止后通知消失 |
| `startingWithNothingPlayingDoesNotStayForeground` | 空闲会话启动**必须被拒绝** |
| `theSessionOutlivesTheServiceObject` | 服务停止后会话对象**仍是同一个** |

**第 5 条正是那个崩溃的回归测试。**

**第 3 条断言标题内容**，不只是「有通知」—— 空标题的通知等于没信息。

---

## 7. MediaSession 接通了什么

| 系统动作 | 接到 |
|---|---|
| `onPlay` | `session.resume()` |
| `onPause` | `session.pause()` |
| `onStop` | `session.stop()` + `stopSelf()` |
| `onSeekTo` | `session.seek(frame)` |

**通知栏、耳机线控、锁屏控件都走这条路。**

---

## 8. 一个诚实的边界

`PlaybackSession` 里**没有** `AudioOutput` 的假实现 —— `AudioOutput` 有私有构造且包着原生句柄，无法伪造。

所以 JVM 测试只用「工厂返回 null」（即无音频设备的状态）来覆盖会话自身的逻辑；真正的开流由设备测试验证。

**我没有假装测过没测的东西。**

---

## 9. 测试总数

| 套件 | 数量 |
|---|---|
| Python | 143 |
| Kotlin JVM | **227** |
| Android Instrumentation | **58** |
| **合计** | **428** |

---

## 10. 仍未做

| 项 | 状态 |
|---|---|
| 音频焦点（来电时暂停） | **未做** |
| 内置 650 条索引 | 未做 |
| 真机 arm64 | NOT_RUN |
| 人耳听音 | NOT_RUN |
| codec 2 解码 | UNRESOLVED |
