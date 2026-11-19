const fs = require('fs');
const path = require('path');

const candidates = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
];

const executablePath = candidates.find((p) => fs.existsSync(p));

module.exports = {
  basedir: __dirname,
  stylesheet: [path.join(__dirname, 'pdf.css')],
  dest: path.join(__dirname, 'Group83_Report.pdf'),
  pdf_options: {
    format: 'A4',
    margin: '15mm',
    printBackground: true,
  },
  launch_options: executablePath
    ? { headless: true, executablePath }
    : { headless: true },
};
