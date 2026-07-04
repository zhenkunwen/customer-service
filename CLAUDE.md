# CLAUDE.md — 项目规则

每次回复开头必须叫我 **大佬**。

## 基本原则

### ❓ 不确定就问
在遇到不确定的情况和细节需要获取时，主动询问，正确优先。不许编造，不许想当然的做假设，不许复杂化，不该碰的代码不许顺手改。

## 编码铁律

### 🚫 非必要不修改核心代码
不要改 framework / 库 / 基础设施代码（Spring Boot config、Vite config、Tailwind 预设等）。如果必须改，先问。

### 🎯 最少代码实现
能改 1 行不改 2 行。不超前设计，不引入未使用的依赖。

### ✨ 代码可读性
- 命名要有意义，注释说"为什么"不说"是什么"
- 不留下注释掉的代码、console.log、调试语句
- 删除无用 import 和变量
- 函数/组件职责单一，长度合理

### 🔍 主动扫描邻近代码
每次修改一个功能后，不要等报错：
1. 主动扫描邻近功能、相关页面的代码，检查是否有同样的 bug 模式
2. 检查 `.catch(() => {})` 等静默吃错误的地方
3. 检查 API 响应格式是否与前端类型定义匹配
4. 发现潜在问题立即修，不要等用户报

### except:pass 零容忍（适用于所有语言）
- Java: 严禁在关键路径使用空的 catch 块，至少要 log.warn
- TypeScript: 禁止空的 catch 块，至少写 `catch { console.error(...) }`
- Python: 严禁空的 except 块，至少写 `logger.exception(...)`

## 工作流程

### 🧪 小任务 + 即时验证
每完成一个小任务必须立即验证（编译 / 运行 / API 测试），确认通过再继续。

### 📋 产品经理评审
每次完成可评审的原子任务后，以产品经理视角评审：
1. 功能是否完整、符合预期
2. 边界情况是否覆盖（空数据、异常输入、并发）
3. 代码质量和可读性
4. 是否引入了不需要的代码

### ☢️ 危险操作先问再做
任何可能造成数据丢失的操作（删数据库、清表、重置数据、批量删除等），必须先向大佬说明原因和影响范围，获得明确同意后才能执行。

### 🧹 测试完立即清理
每次测试完成后，删除测试过程中产生的临时代码、调试数据、curl 残留、测试用户/计划等无用产物。

## 技术栈

- 后端: Java 17 + Spring Boot 3 WebFlux + JPA + MySQL/H2 + Kafka
- 前端: React 18 + TypeScript + Vite 5 + Zustand 4 + Tailwind CSS 3 + Axios
- 测试: Python 3 + pytest + requests（集成测试）
- 构建: `cd backend && mvn compile` / `cd frontend && npm run dev`

## 测试

### Eval 集成测试 — pytest 脚本
```python
# tests/eval_test.py — Eval API 集成测试
import os, requests
BASE = os.getenv("EVAL_BASE_URL", "http://localhost:8080")
HDR = {"X-API-Key": os.getenv("EVAL_API_KEY", "change-me")}

def test_list():     r = requests.get(f"{BASE}/api/v1/eval/testcases", headers=HDR); assert r.status_code==200; print(f"[PASS] {len(r.json())} cases")
def test_gen():      r = requests.post(f"{BASE}/api/v1/eval/generate-testcases?count=3", headers=HDR); assert r.status_code==200; print(f"[PASS] {r.text}")
def test_run():      r = requests.post(f"{BASE}/api/v1/eval/run", headers=HDR); d=r.json()["summary"]; assert r.status_code==200; print(f"[PASS] {d['totalCases']} cases, recall={d['avgRecall']:.2f}")
def test_history():  r = requests.get(f"{BASE}/api/v1/eval/history", headers=HDR); assert r.status_code==200; print(f"[PASS] {len(r.json()['content'])} reports")
```
```bash
# 运行
pip install pytest requests
cd tests && pytest eval_test.py -v
```
