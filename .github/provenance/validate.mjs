import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Load the actual pinned shared validator, including strict YAML/schema rules.
const { parseConfiguration, hashConfiguration } = await import(pathToFileURL(resolve(process.argv[2])).href);
const configuration = parseConfiguration(readFileSync('provenance.yml', 'utf8'));
console.log(`Committed provenance.yml schema validation passed; SHA-256: ${hashConfiguration(configuration)}`);
