import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * E2E 需要一个装了后端依赖的 Python。
 * 优先用仓库内 venv，避免依赖调用方的 PATH 配置。
 */
const venvPython = fileURLToPath(
  new URL('../../../services/api/.venv/Scripts/python.exe', import.meta.url),
);

export const PYTHON = existsSync(venvPython) ? venvPython : 'python';
