/*!
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Helper script to convert then JSON output from Jetpack Benchmark to CSV
// Invoke as such: $ node convertToCSV.js ./result.json

function extractPropNames(name) {
  const propPortion = name.substring(name.indexOf("-") + 2, name.length - 1);
  return propPortion.split(/\ |,\ /).filter((_, i) => i % 2 === 1);
}

function extractPropValues(name) {
  const propPortion = name.substring(name.indexOf("-") + 2, name.length - 1);
  return propPortion.split(/\ |,\ /).filter((_, i) => i % 2 === 0);
}

function extractName(name) {
  return name.substring(0, name.indexOf("["));
}

function matches(row1, row2) {
  if (row1.length !== row2.length) {
    return false;
  }

  for (let i = 0; i < row1.length; ++i) {
    if (row1[i] === row2[i]) {
      continue;
    } else if (row1[i] === "") {
      continue;
    } else if (row2[i] === "") {
      continue;
    }
    return false;
  }
  return true;
}

function merge(row1, row2) {
  let result = [];
  for (let i = 0; i < row1.length; ++i) {
    if (row1[i] === row2[i]) {
      result.push(row1[i]);
    } else if (row1[i] !== "") {
      result.push(row1[i]);
    } else {
      result.push(row2[i]);
    }
  }
  return result;
}

const filename = process.argv[2];
const json = require(filename);

let csv = "";

const props = extractPropNames(json.benchmarks[0].name);
const names = json.benchmarks
  .map((b) => b.name)
  .map((n) => extractName(n))
  .filter((v, i, arr) => arr.indexOf(v) === i);

csv += [...props, ...names].join(",") + "\n";

let data = {};

let testCases = [];

for (const benchmark of json.benchmarks) {
  const values = extractPropValues(benchmark.name);
  testCases.push([
    ...values,
    ...names.map((n) =>
      n === extractName(benchmark.name) ? benchmark.metrics.timeNs.median : ""
    ),
  ]);
}

testCases = testCases.reduce((previousValue, currentValue) => {
  if (
    previousValue.length !== 0 &&
    matches(previousValue[previousValue.length - 1], currentValue)
  ) {
    previousValue[previousValue.length - 1] = merge(
      previousValue[previousValue.length - 1],
      currentValue
    );
  } else {
    previousValue.push(currentValue);
  }
  return previousValue;
}, []);

csv += testCases
  .map((v) => JSON.stringify(v))
  .map((v) => v.substring(1, v.length - 1))
  .join("\n");

console.log(csv);
