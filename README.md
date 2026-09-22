# QuoteMemeNext

**English** | [中文](README.zh-CN.md)

> One-tap quote-image generator for QQ, built on QFun.

🎉 Quote Meme **Next**, rebuilt on a brand-new architecture 🎉

Rebuilt from the ground up on top of the original "名言作图" script by **沄** and **MySQLdisappoint** — lower layers refactored, upper layers rewritten, extended and optimized. Many thanks to the original authors.

Released under the MIT License. Repository: https://github.com/YiJieqwq/QuoteMemeNext

## Defaults

Command invocation allowed · invocation by others disabled · pure-white text · pure-black background · fully opaque pure-black gradient overlay.

## Usage

**Generate an image:** long-press any message, then tap **"生成名言" (Generate Quote)**.

**Generate via command:** enable *"Allow the `/名言` command to generate images"* in the floating menu, then reply to the target message and send only `/名言`. It generates automatically. *"Allow others to use"* can be enabled at the same time to share it with other people.

**Supported formats:** text messages are fully supported. Long-press generation works for image messages; command invocation may fail there (running in a constrained environment — no better workaround for now).

**Custom styles:** tap *"Configure quote image element colors"* in the floating menu to freely adjust text, background, and gradient overlay colors.

## Avatar Cache

A cached avatar starts a countdown after its first use and is deleted ten seconds later. If the same avatar is requested again during the countdown, the timer is cleared and the ten-second countdown restarts.
