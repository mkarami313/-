#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Minimal HTTPS CONNECT proxy for FCM egress.

Runs on box82 (German IP) and is reachable only from tizbin over a reverse SSH tunnel.
It tunnels end-to-end TLS (CONNECT), so the service account key and FCM logic stay on
tizbin; only the network egress is box82. Restricted to Google's FCM/OAuth hosts so it
can never act as an open relay.

Listens on 127.0.0.1:12080 (matches the ask-tunnel reverse forward to tizbin).
"""
import socket
import select
import threading

LISTEN = ("127.0.0.1", 12080)
ALLOWED_SUFFIXES = (".googleapis.com", ".google.com", ".gstatic.com")
ALLOWED_PORTS = {443}


def allowed(host, port):
    host = host.lower()
    return port in ALLOWED_PORTS and any(
        host == s[1:] or host.endswith(s) for s in ALLOWED_SUFFIXES
    )


def pipe(a, b):
    try:
        while True:
            r, _, _ = select.select([a, b], [], [], 60)
            if not r:
                break
            for s in r:
                data = s.recv(65536)
                if not data:
                    return
                (b if s is a else a).sendall(data)
    except Exception:
        pass
    finally:
        for s in (a, b):
            try:
                s.close()
            except Exception:
                pass


def handle(client):
    try:
        client.settimeout(15)
        req = b""
        while b"\r\n\r\n" not in req:
            chunk = client.recv(4096)
            if not chunk:
                client.close(); return
            req += chunk
            if len(req) > 16384:
                client.close(); return
        line = req.split(b"\r\n", 1)[0].decode("latin1")
        parts = line.split()
        if len(parts) < 2 or parts[0].upper() != "CONNECT":
            client.sendall(b"HTTP/1.1 405 Method Not Allowed\r\n\r\n"); client.close(); return
        hostport = parts[1]
        host, _, port = hostport.partition(":")
        port = int(port or "443")
        if not allowed(host, port):
            client.sendall(b"HTTP/1.1 403 Forbidden\r\n\r\n"); client.close(); return
        upstream = socket.create_connection((host, port), timeout=15)
        client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        client.settimeout(None)
        pipe(client, upstream)
    except Exception:
        try:
            client.close()
        except Exception:
            pass


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(LISTEN)
    srv.listen(128)
    print("fcm-proxy listening on %s:%d" % LISTEN, flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()


if __name__ == "__main__":
    main()
