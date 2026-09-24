import { marked } from 'marked'
import hljs from 'highlight.js'

marked.setOptions({
  breaks: true,
  gfm: true
})

const renderer = new marked.Renderer()

renderer.code = function ({ text, lang }) {
  const validLang = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const highlighted = hljs.highlight(text, { language: validLang }).value
  const langLabel = validLang !== 'plaintext' ? validLang.toUpperCase() : ''
  return `<div class="code-block"><div class="code-block-header">${langLabel}</div><pre><code class="hljs language-${validLang}">${highlighted}</code></pre></div>`
}

renderer.codespan = function ({ text }) {
  return `<code class="inline-code">${text}</code>`
}

marked.use({ renderer })

const ARTIFACT_PATTERNS = [
  /：text/g,
  /：code/g,
  /【text】/g,
  /【code】/g,
]

function cleanArtifacts(content) {
  let cleaned = content
  for (const pattern of ARTIFACT_PATTERNS) {
    cleaned = cleaned.replace(pattern, '')
  }
  return cleaned
}

function closeUnclosedFences(content) {
  const fences = content.match(/```/g)
  if (!fences) return content
  if (fences.length % 2 !== 0) {
    return content + '\n```'
  }
  return content
}

function handleStreamingFences(content) {
  const match = content.match(/```/g)
  if (!match || match.length % 2 === 0) return content

  const lastFence = content.lastIndexOf('```')
  const afterFence = content.substring(lastFence + 3)

  if (afterFence.trim() === '') {
    return content.substring(0, lastFence).trimEnd()
  }

  return content
}

function normalizeMarkdown(content) {
  let normalized = content
  normalized = normalized.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  normalized = normalized.replace(/^([-*+])([^\s-*+])/gm, '$1 $2')
  return normalized
}

export function renderMarkdown(content) {
  if (!content) return ''
  const cleaned = cleanArtifacts(content)
  const normalized = normalizeMarkdown(cleaned)
  return marked.parse(normalized)
}

export function renderStreamingMarkdown(content) {
  if (!content) return ''
  const cleaned = cleanArtifacts(content)
  const normalized = normalizeMarkdown(cleaned)
  const safe = handleStreamingFences(normalized)
  return marked.parse(safe)
}