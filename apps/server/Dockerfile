# Build from the repository root: docker build -f apps/server/Dockerfile .
FROM node:22-slim
RUN corepack enable
WORKDIR /app
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml tsconfig.base.json ./
COPY packages/core packages/core
COPY apps/server apps/server
RUN pnpm install --frozen-lockfile --filter @foco/server...
ENV NODE_ENV=production PORT=8787
EXPOSE 8787
CMD ["pnpm", "--filter", "@foco/server", "start"]
