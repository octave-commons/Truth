import { Plugin, tool } from "@opencode-ai/plugin"
import * as fs from "node:fs"
import * as os from "node:os"
import * as path from "node:path"

function claudeMemoryDir(projectDir: string): string {
  const slug = "-" + projectDir.replace(/^\//, "").replace(/\//g, "-")
  return path.join(os.homedir(), ".claude", "projects", slug, "memory")
}

function indexPath(projectDir: string): string {
  return path.join(claudeMemoryDir(projectDir), "MEMORY.md")
}

function receiptPath(projectDir: string): string {
  const etaMuPath = path.join(projectDir, ".ημ", "receipts.edn")
  if (fs.existsSync(etaMuPath)) return etaMuPath
  return path.join(projectDir, "receipts.edn")
}

type MemoryEntry = { name: string; file: string; description: string }

function parseIndex(text: string): MemoryEntry[] {
  const entries: MemoryEntry[] = []
  for (const line of text.split("\n")) {
    const match = line.match(/^- \[([^\]]+)\]\(([^)]+)\)\s*[—-]\s*(.*)$/)
    if (match) entries.push({ name: match[1], file: match[2], description: match[3] })
  }
  return entries
}

function renderIndex(entries: MemoryEntry[]): string {
  return entries.map((e) => `- [${e.name}](${e.file}) — ${e.description}`).join("\n") + "\n"
}

function slugify(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")
}

function ensureMemoryDir(projectDir: string): string {
  const dir = claudeMemoryDir(projectDir)
  fs.mkdirSync(dir, { recursive: true })
  return dir
}

function readIndex(projectDir: string): MemoryEntry[] {
  const p = indexPath(projectDir)
  if (!fs.existsSync(p)) return []
  return parseIndex(fs.readFileSync(p, "utf-8"))
}

function writeIndex(projectDir: string, entries: MemoryEntry[]): void {
  fs.writeFileSync(indexPath(projectDir), renderIndex(entries))
}

function readMemoryFile(projectDir: string, fileName: string): string | null {
  const p = path.join(claudeMemoryDir(projectDir), fileName)
  if (!fs.existsSync(p)) return null
  return fs.readFileSync(p, "utf-8")
}

function stripFrontmatter(text: string): string {
  const match = text.match(/^---\s*\n[\s\S]*?\n---\s*\n?/)
  return match ? text.slice(match[0].length) : text
}

function toEdn(value: unknown): string {
  if (value === null || value === undefined) return "nil"
  if (typeof value === "string") {
    if (value.startsWith(":")) return value
    return JSON.stringify(value)
  }
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  if (Array.isArray(value)) return `[${value.map(toEdn).join(" ")}]`
  if (typeof value === "object") {
    const entries = Object.entries(value)
      .filter(([, v]) => v !== undefined)
      .map(([k, v]) => `:${k} ${toEdn(v)}`)
    return `{${entries.join(" ")}}`
  }
  return JSON.stringify(String(value))
}

export default (async (input) => {
  const memoryDir = ensureMemoryDir(input.directory)

  return {
    tool: {
      claude_memory_list: tool({
        description: "List Claude Code project memories by reading MEMORY.md",
        args: {},
        async execute() {
          const entries = readIndex(input.directory)
          if (entries.length === 0) return "No memories found in " + memoryDir
          return entries.map((e) => `${e.name} (${e.file}) — ${e.description}`).join("\n")
        },
      }),

      claude_memory_read: tool({
        description: "Read a specific Claude Code memory by name or slug",
        args: {
          name: tool.schema.string().describe("Memory name or slug"),
        },
        async execute(args) {
          const slug = slugify(args.name)
          const candidates = [`${slug}.md`, `${args.name}.md`]
          for (const file of candidates) {
            const text = readMemoryFile(input.directory, file)
            if (text !== null) return text
          }
          const entries = readIndex(input.directory)
          const entry = entries.find((e) => slugify(e.name) === slug || e.name === args.name)
          if (!entry) return `Memory "${args.name}" not found`
          const text = readMemoryFile(input.directory, entry.file)
          return text ?? `Memory "${args.name}" is indexed but file ${entry.file} is missing`
        },
      }),

      claude_memory_write: tool({
        description: "Create or update a Claude Code memory and sync MEMORY.md",
        args: {
          name: tool.schema.string().describe("Short memory name/title"),
          description: tool.schema.string().describe("One-line summary for the index"),
          content: tool.schema.string().describe("Markdown body of the memory"),
          overwrite: tool.schema.boolean().optional().describe("Allow overwriting an existing memory"),
        },
        async execute(args, ctx) {
          const file = `${slugify(args.name)}.md`
          const filePath = path.join(memoryDir, file)

          if (fs.existsSync(filePath) && args.overwrite !== true) {
            return `Memory ${file} already exists. Pass overwrite=true to replace it.`
          }

          const frontmatter = [
            "---",
            `name: ${slugify(args.name)}`,
            `description: ${args.description}`,
            "metadata:",
            "  node_type: memory",
            "  type: project",
            `  originSessionId: ${ctx.sessionID}`,
            "---",
            "",
          ].join("\n")

          fs.writeFileSync(filePath, frontmatter + args.content.trim() + "\n")

          const entries = readIndex(input.directory)
          const existing = entries.findIndex((e) => e.file === file)
          const entry: MemoryEntry = { name: args.name, file, description: args.description }
          if (existing >= 0) entries[existing] = entry
          else entries.push(entry)

          writeIndex(input.directory, entries)
          return `Wrote memory ${file} and updated MEMORY.md`
        },
      }),

      claude_memory_search: tool({
        description: "Search Claude Code memories for a keyword or phrase",
        args: {
          query: tool.schema.string().describe("Keyword or phrase to search for"),
        },
        async execute(args) {
          const entries = readIndex(input.directory)
          const lower = args.query.toLowerCase()
          const hits: string[] = []

          for (const entry of entries) {
            const text = readMemoryFile(input.directory, entry.file) ?? ""
            const body = stripFrontmatter(text).toLowerCase()
            if (
              entry.name.toLowerCase().includes(lower) ||
              entry.description.toLowerCase().includes(lower) ||
              body.includes(lower)
            ) {
              const lines = body.split("\n").filter((l) => l.toLowerCase().includes(lower))
              hits.push(`- ${entry.name} (${entry.file})`)
              for (const line of lines.slice(0, 3)) {
                hits.push(`    ${line.trim()}`)
              }
            }
          }

          return hits.length > 0 ? hits.join("\n") : `No matches for "${args.query}"`
        },
      }),

      receipt_append: tool({
        description: "Append a receipt line to receipts.edn for the Receipt River",
        args: {
          kind: tool.schema.enum([":observation", ":test-run", ":build", ":decision", ":fix", ":push-truth", ":catalog"]).describe("Receipt kind"),
          origin: tool.schema.string().describe("Task or path reference"),
          note: tool.schema.string().describe("Human-readable summary"),
          tests: tool.schema.string().optional().describe("Test command and result summary"),
          manifest: tool.schema.string().optional().describe("Comma-separated list of changed files"),
          refs: tool.schema.string().optional().describe("Comma-separated refs (commits, issues, session ids)"),
          decisions: tool.schema.string().optional().describe("Decision record"),
        },
        async execute(args, ctx) {
          const p = receiptPath(input.directory)
          const manifest = args.manifest ? args.manifest.split(",").map((s) => s.trim()) : []
          const refs = args.refs ? args.refs.split(",").map((s) => s.trim()) : []

          const line = {
            ts: new Date().toISOString(),
            kind: args.kind,
            origin: args.origin,
            owner: "opencode",
            dod: "",
            pi: ctx.sessionID,
            host: input.directory.split(path.sep).slice(-2).join("/"),
            manifest,
            refs,
            note: args.note,
            ...(args.tests ? { tests: args.tests } : {}),
            ...(args.decisions ? { decisions: args.decisions } : {}),
          }

          fs.appendFileSync(p, toEdn(line) + "\n")
          return `Appended receipt to ${p}`
        },
      }),
    },
  }
}) satisfies Plugin
