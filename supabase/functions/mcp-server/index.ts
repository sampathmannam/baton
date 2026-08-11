// Supabase Edge Function: Baton MCP server
// Speaks MCP over Streamable HTTP. Auth via Supabase JWT in Authorization header.
// M0 scope: read-only. Exposes one resource: baton://persons.

import { createClient } from "@supabase/supabase-js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { corsHeaders, handleCors } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  const cors = handleCors(req);
  if (cors) return cors;

  // Auth: extract Supabase access token from Authorization header.
  // config.toml has `verify_jwt = true` so Supabase has already validated
  // the token before this handler runs. The header is still needed so
  // the inner Supabase client uses the user's identity (for RLS).
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return new Response("Unauthorized", {
      status: 401,
      headers: corsHeaders,
    });
  }
  const accessToken = authHeader.replace("Bearer ", "");

  // Build a Supabase client scoped to the calling user — RLS applies.
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    {
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
    },
  );

  // Build the MCP server with one resource: baton://persons.
  const server = new McpServer({
    name: "baton-mcp",
    version: "0.1.0",
  });

  server.resource(
    "persons",
    "baton://persons",
    async (uri) => {
      const { data, error } = await supabase
        .from("persons")
        .select("id, name, designation, station, phone")
        .is("deleted_at", null);
      if (error) {
        return {
          contents: [
            { uri: uri.href, text: `Error: ${error.message}`, mimeType: "text/plain" },
          ],
        };
      }
      return {
        contents: [
          {
            uri: uri.href,
            text: JSON.stringify(data, null, 2),
            mimeType: "application/json",
          },
        ],
      };
    },
  );

  // Streamable HTTP transport
  const transport = new StreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
  });
  await server.connect(transport);

  return transport.handleRequest(req);
});
