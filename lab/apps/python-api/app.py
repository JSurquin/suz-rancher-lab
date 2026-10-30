from flask import Flask, jsonify

app = Flask(__name__)


@app.get("/")
def home():
    return jsonify(
        app="python-api",
        message="Hello from Python 3.12",
        stack="Docker + Kubernetes",
        formation="SUSE Rancher 2026",
    )


@app.get("/health")
def health():
    return jsonify(status="ok")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
