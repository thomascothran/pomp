import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";

import { Scanner } from "@tailwindcss/oxide";

const cwd = process.cwd();
const outputPath = path.resolve(cwd, "resources/pomp-tailwind-classes.txt");

const scanner = new Scanner({
  sources: [
    { base: cwd, pattern: "src/**/*.{clj,cljc,cljs,js}", negated: false },
    { base: cwd, pattern: "resources/public/**/*.js", negated: false },
  ],
});

const uniqueCandidates = [...new Set(scanner.scan())].sort();

await fs.writeFile(outputPath, `${uniqueCandidates.join("\n")}\n`, "utf8");

console.log(`Wrote ${uniqueCandidates.length} tokens to ${path.relative(cwd, outputPath)}`);
