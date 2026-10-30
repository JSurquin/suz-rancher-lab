# syntax=docker/dockerfile:1
FROM node:22-alpine
WORKDIR /app
COPY package.json ./
RUN --mount=type=secret,id=proxy_ca \
    if [ -s /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm install --omit=dev
COPY server.mjs ./
ENV PORT=3001
EXPOSE 3001
CMD ["node", "server.mjs"]
