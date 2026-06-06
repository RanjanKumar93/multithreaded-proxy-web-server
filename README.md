# Java HTTP/HTTPS Proxy Server

A lightweight, multithreaded HTTP/HTTPS proxy server built from scratch in Java using standard Blocking I/O (`java.net.Socket`).

---

## 📋 Prerequisites

* **Java Development Kit (JDK) 8** or higher installed on your system.
* **Git** installed on your system (optional, to clone).

---

## 🚀 How to Clone and Run

### 1. Clone the Repository

```bash
git clone https://github.com/RanjanKumar93/multithreaded-proxy-web-server.git
cd multithreaded-proxy-web-server/src
```

### 2. Run Files

Navigate to the directory containing your project folder and Run the Project:
Or Compile:
```bash
javac *.java
```

### 3. Run the Proxy Server

Launch the central server application engine from your root source folder (starts listening on port 8080):

```bash
java ProxyServer
```

---

## 🧪 How to Test It

Open a second terminal window and route traffic through your proxy using either of these quick tests:

### Test HTTPS (Google Home Page)

```bash
curl -x http://localhost:8080 https://www.google.com/
```

### Test HTTPS via Windows PowerShell

```powershell
Invoke-WebRequest -Proxy http://localhost:8080 -Uri https://google.com -MaximumRedirection 5
```

---

## 📂 Project Structure

* `ProxyServer.java` — Core launcher, instantiates port and the LRU cache.
* `RequestHandler.java` — Inspects protocol methods (GET vs CONNECT) and directs traffic.
* `HttpProcessor.java` — Manages plain text header processing and caching.
* `HttpsTunnel.java` — Spawns separate pipeline threads for blind bidirectional data tunneling.

---

## 📜 License

This project is open-source and available under the MIT License.
