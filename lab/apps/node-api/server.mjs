import express from 'express'

const app = express()
const port = Number(process.env.PORT ?? 3001)

app.get('/', (_req, res) => {
  res.json({
    app: 'node-api',
    message: 'Hello from Node.js 22',
    stack: 'Docker + Kubernetes',
    formation: 'SUSE Rancher 2026',
  })
})

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' })
})

app.listen(port, () => {
  console.log(`node-api listening on :${port}`)
})
