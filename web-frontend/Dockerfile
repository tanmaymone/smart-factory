FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG BACKEND_ORIGIN=http://central-backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
# Visible in the browser bundle until M4.2. Must match APP_BOOTSTRAP_TOKEN on central-backend.
ARG NEXT_PUBLIC_BOOTSTRAP_TOKEN=
ENV NEXT_PUBLIC_BOOTSTRAP_TOKEN=$NEXT_PUBLIC_BOOTSTRAP_TOKEN
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
ARG BACKEND_ORIGIN=http://central-backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
ARG NEXT_PUBLIC_BOOTSTRAP_TOKEN=
ENV NEXT_PUBLIC_BOOTSTRAP_TOKEN=$NEXT_PUBLIC_BOOTSTRAP_TOKEN
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs
COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=8 \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1
CMD ["node", "server.js"]
