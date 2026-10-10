# Debugging and profiling toolbox (as of 2026-10)

Commands are minimal starting points; check `--help` on the installed version. Bind every debug port to localhost.

## Browser
- DevTools Sources: breakpoints, conditional breakpoints, logpoints (no code change), "pause on caught/uncaught exceptions", XHR/fetch and DOM breakpoints.
- Performance panel: record an interaction, find long tasks and long animation frames; the live metrics view shows LCP/CLS/INP for your own session and can overlay field data.
- INP: find the slow interaction, split it into input delay / processing / presentation; fix long handlers (yield with `scheduler.yield()` where supported, move work off the main thread), avoid layout thrash.
- Memory panel: take heap snapshot, do the action N times, snapshot again, compare "Objects allocated between snapshots"; detached DOM nodes are a classic leak.
- Network: throttling, "copy as cURL" to reproduce outside the browser, block request URL to test failure paths.
- Agents: `chrome-devtools-mcp` and `@playwright/mcp` let a coding agent drive and inspect a real browser.

## Node.js
- `node --inspect=127.0.0.1:9229 app.js` (or `--inspect-brk`), attach Chrome `chrome://inspect` or VS Code.
- CPU: `node --cpu-prof app.js` -> `.cpuprofile` (open in DevTools); heap: `--heap-prof`, `--heapsnapshot-signal=SIGUSR2`.
- Flame graphs: 0x; `--trace-gc` and `--trace-deopt` for GC and JIT questions. Unhandled rejections: run with the default (throw) and read the stack.
- Async stack traces are on by default in DevTools; `AsyncLocalStorage` for request IDs in logs.

## Python
- `breakpoint()` (honours `PYTHONBREAKPOINT`), `python -m pdb -c continue script.py` for post-mortem, `pytest --pdb` / `--lf -x`.
- 3.14+: `python -m pdb -p PID` attaches to a live process; `python -m asyncio ps PID` / `pstree PID` show stuck tasks.
- 3.15+: `python -m profiling.sampling run script.py` or `attach PID` (flame graph, Firefox Profiler output); `cProfile` remains.
- Older: `py-spy top --pid PID`, `py-spy dump --pid PID` (stacks of all threads, finds deadlocks), `py-spy record -o out.svg`.
- Memory: `memray run` + `memray flamegraph`, `tracemalloc` snapshots compared over time. Faulthandler: `python -X faulthandler` for segfault stacks.
- Remote/IDE: debugpy (VS Code). Disable remote attach in hardened prod: `PYTHON_DISABLE_REMOTE_DEBUG=1`.

## Go
- Delve: `dlv debug`, `dlv test ./pkg -- -test.run TestX`, `dlv attach PID`, `dlv core ./bin core`.
- pprof: import `net/http/pprof` on an internal-only port; `go tool pprof -http=:0 http://127.0.0.1:6060/debug/pprof/profile?seconds=30`;
  heap diff with `-base`; `goroutine?debug=2` for full stacks; 1.27 `goroutineleak` profile.
- Tracing: `go test -trace trace.out` + `go tool trace`; `runtime/trace.FlightRecorder` (1.25) keeps the last seconds in memory to dump on an anomaly.
- Races: `go test -race`; timing bugs: `testing/synctest`. Crash: `GOTRACEBACK=all` (or `crash` for a core dump).

## JVM
- `jcmd PID Thread.print` (deadlocks are reported at the end), `jcmd PID GC.heap_info`, `jcmd PID GC.heap_dump /tmp/h.hprof` (analyse in Eclipse MAT or VisualVM).
- JFR: `jcmd PID JFR.start duration=60s filename=rec.jfr`, open in JDK Mission Control; low overhead, fine in production.
- async-profiler (`asprof -d 30 -f flame.html PID`): CPU, allocation, lock and wall-clock flame graphs.
- Virtual threads: thread dumps in JSON via `jcmd PID Thread.dump_to_file -format=json file` (JDK 21+).
- Remote debug: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005`.

## Android
- Android Studio Profiler (CPU, memory, energy), heap dumps, "Find leaks"; Layout Inspector + Compose recomposition counts.
- Perfetto (`ui.perfetto.dev`, system traces) for jank and startup; Macrobenchmark for repeatable numbers.
- StrictMode in debug builds (disk/network on main thread, leaked closables); LeakCanary in debug builds only.
- `adb logcat --pid=$(adb shell pidof -s <package>)`, `adb bugreport`, ANR traces; upload R8 mapping files so Play Console stacks are readable.

## Apple platforms
- Xcode debugger (lldb: `po`, `v`, `bt all`), symbolic and exception breakpoints, Memory Graph Debugger, View Hierarchy.
- Instruments: Time Profiler, Allocations, Leaks, Hangs, SwiftUI, Network; Thread Sanitizer and Address Sanitizer in the scheme.
- MetricKit and Xcode Organizer for field crashes/hangs; keep dSYMs for symbolication.

## Native and system
- gdb/lldb: `bt full`, `thread apply all bt`, `info locals`, watchpoints; `coredumpctl gdb` on systemd hosts.
- Sanitizers (`-fsanitize=address,undefined` or `thread`), valgrind for older toolchains.
- `strace -f -tt -e trace=file,network -p PID`, `ltrace` for library calls, `lsof -p PID`, `ss -tanp`, `perf record -g` + `perf report`, `bpftrace` one-liners.

## Network
- `curl -v` / `curl --trace-ascii -`, HTTPie for readable JSON, `openssl s_client -connect host:443 -servername host` for TLS, `dig +trace` for DNS.
- mitmproxy (or Charles/Proxyman) for mobile/app traffic; certificate pinning must be relaxed in debug builds only.
- tcpdump (`tcpdump -i any -w cap.pcap port 443`) + Wireshark; with `SSLKEYLOGFILE` you can decrypt your own TLS sessions.

## Reading stack traces and dumps
- Find the first frame in your own code; in JVM traces read the deepest "Caused by"; in async JS look for the "await" segments.
- Minified/obfuscated: upload source maps (web, privately), R8 mapping (Android), dSYMs (iOS) to the crash tool, never ship them publicly unless intended.
- A core or heap dump is a snapshot of memory: it contains secrets and user data. Handle like production data.
