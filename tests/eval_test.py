"""Eval API 集成测试 — 用 pytest 跑"""

import os
import requests

BASE_URL = os.getenv("EVAL_BASE_URL", "http://localhost:8080")
HEADERS = {"X-API-Key": os.getenv("EVAL_API_KEY", "change-me")}


def test_list_testcases():
    r = requests.get(f"{BASE_URL}/api/v1/eval/testcases", headers=HEADERS)
    assert r.status_code == 200
    data = r.json()
    assert isinstance(data, list)
    assert len(data) > 0
    print(f"[PASS] testcases count: {len(data)}")


def test_generate():
    r = requests.post(f"{BASE_URL}/api/v1/eval/generate-testcases?count=3", headers=HEADERS)
    assert r.status_code == 200
    print(f"[PASS] generate: {r.text}")


def test_run_eval():
    r = requests.post(f"{BASE_URL}/api/v1/eval/run", headers=HEADERS)
    assert r.status_code == 200
    data = r.json()
    assert "summary" in data
    s = data["summary"]
    print(f"[PASS] eval: {s['totalCases']} cases, recall={s['avgRecall']:.2f}")


def test_history():
    r = requests.get(f"{BASE_URL}/api/v1/eval/history", headers=HEADERS)
    assert r.status_code == 200
    data = r.json()
    assert "content" in data  # Page 响应
    print(f"[PASS] history: {len(data['content'])} reports")


def test_compare():
    # 先查历史拿两个 ID
    r = requests.get(f"{BASE_URL}/api/v1/eval/history?size=2", headers=HEADERS)
    ids = [item["id"] for item in r.json()["content"]]
    if len(ids) >= 2:
        r = requests.get(f"{BASE_URL}/api/v1/eval/compare?idA={ids[0]}&idB={ids[1]}", headers=HEADERS)
        assert r.status_code == 200
        print(f"[PASS] compare: 2 reports compared")
