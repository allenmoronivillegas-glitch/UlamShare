const http = require('http');
const fs = require('fs');
const path = require('path');

const host = '127.0.0.1';
const port = 5500;
const root = process.cwd();

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8'
};

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type });
  res.end(body);
}

const server = http.createServer((req, res) => {
  try {
    const reqUrl = (req.url || '/').split('?')[0].split('#')[0];
    const safeUrl = decodeURIComponent(reqUrl);
    const normalized = safeUrl === '/' ? '/index.html' : safeUrl;
    const filePath = path.normalize(path.join(root, normalized));

    if (!filePath.startsWith(root)) {
      return send(res, 403, 'Forbidden');
    }

    fs.stat(filePath, (statErr, stat) => {
      if (statErr) {
        return send(res, 404, 'Not Found');
      }

      const target = stat.isDirectory() ? path.join(filePath, 'index.html') : filePath;
      fs.readFile(target, (readErr, data) => {
        if (readErr) {
          return send(res, 404, 'Not Found');
        }

        const ext = path.extname(target).toLowerCase();
        const mime = mimeTypes[ext] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': mime });
        res.end(data);
      });
    });
  } catch (err) {
    send(res, 500, 'Server Error');
  }
});

server.listen(port, host, () => {
  console.log(`Serving ${root} at http://${host}:${port}`);
});
