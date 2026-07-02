// Extract all prompt constants from App.vue into src/prompts/defaults.js
const fs = require('fs');
const path = require('path');

const srcPath = path.resolve(__dirname, '../src/App.vue');
const dstPath = path.resolve(__dirname, '../src/prompts/defaults.js');

let src = fs.readFileSync(srcPath, 'utf8');
const lines = src.split('\n');

const promptNames = [
  'IMAGE_IMPORT_DEFAULT_PROMPT',
  'IMAGE_IMPORT_PROMPT_REFERENCE',
  'IMAGE_IMPORT_PROMPT_PHRASE',
  'SCORING_SYSTEM_PROMPT',
  'SEGMENT_PROMPT',
  'WAVE_SYSTEM_PROMPT',
  'REVERSE_SCORING_PROMPT',
];

// Find each prompt's line range
const found = [];
for (let i = 0; i < lines.length; i++) {
  for (const name of promptNames) {
    if (lines[i].includes(`const ${name} = \``)) {
      let j = i;
      while (j < lines.length && !lines[j].trimEnd().endsWith('`;')) j++;
      found.push({ name, start: i, end: j });
      i = j; // skip to end
      break;
    }
  }
}

// Sort by start line (descending for safe deletion)
found.sort((a, b) => b.start - a.start);

// Build the output file
let out = '// 提示词常量 — 从 App.vue 提取\n// 这些是发送给 AI 的系统提示词和用户提示词模板\n\n';
for (const f of [...found].reverse()) {
  const block = lines.slice(f.start, f.end + 1).join('\n');
  out += block + '\n\n';
}
out += `export {
${found.map(f => '  ' + f.name).join(',\n')}
};\n`;

fs.writeFileSync(dstPath, out, 'utf8');
console.log(`Wrote ${found.length} prompts to ${dstPath}`);

// Remove prompts from App.vue (in reverse order to preserve line numbers)
for (const f of found) {
  lines.splice(f.start, f.end - f.start + 1);
  console.log(`Removed ${f.name} (lines ${f.start + 1}-${f.end + 1})`);
}

// Add import at top of script section
const scriptLine = lines.findIndex(l => l.includes('<script setup>'));
lines.splice(scriptLine + 1, 0,
  `import { ${found.map(f => f.name).join(', ')} } from './prompts/defaults.js'`);
console.log('Added import statement');

fs.writeFileSync(srcPath, lines.join('\n'), 'utf8');
console.log('Updated App.vue');
