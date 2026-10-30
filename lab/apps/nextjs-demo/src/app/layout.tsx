import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Next.js Demo - SUSE Rancher Lab',
  description: 'Application Next.js de démonstration pour la formation',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="fr">
      <body style={{ fontFamily: 'system-ui, sans-serif', margin: '2rem' }}>
        {children}
      </body>
    </html>
  )
}
