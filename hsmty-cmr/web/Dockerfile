# 前端镜像：Node 20.19.5 构建 + Nginx 托管（qiankun 子应用）
FROM node:20.19.5-alpine AS build
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@10.17.1 --activate

COPY package.json pnpm-lock.yaml* ./
RUN pnpm install --frozen-lockfile || pnpm install

COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 构建产物的 base 是 /sdom-hsmty/，必须放在同名子目录下，否则静态资源 404
COPY --from=build /app/dist /usr/share/nginx/html/sdom-hsmty
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
