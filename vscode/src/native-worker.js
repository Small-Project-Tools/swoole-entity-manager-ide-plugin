/* Copyright 2026 - Sebastien Kus. Licensed under MIT. */
'use strict';
const {parentPort,workerData} = require('node:worker_threads');
const {indexProject} = require('./native-index');
try {
  parentPort.postMessage({index:indexProject(workerData.root)});
} catch (error) {
  parentPort.postMessage({error:String(error)});
}
