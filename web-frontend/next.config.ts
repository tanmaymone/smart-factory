import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [
      {
        source: "/api/telemetry/:path*",
        destination: `${backendOrigin}/api/telemetry/:path*`,
      },
      {
        source: "/api/registry/:path*",
        destination: `${backendOrigin}/api/registry/:path*`,
      },
      {
        source: "/api/commands/:path*",
        destination: `${backendOrigin}/api/commands/:path*`,
      },
      {
        source: "/api/commands",
        destination: `${backendOrigin}/api/commands`,
      },
    ];
  },
};

export default nextConfig;
