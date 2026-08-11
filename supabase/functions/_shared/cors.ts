// CORS headers + preflight handler shared by all Supabase Edge Functions
// in this project. Baton's MCP server is called from desktop MCP clients
// (Cursor, Claude Desktop) which run on different origins; CORS must allow
// those.

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export function handleCors(req: Request): Response | null {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }
  return null;
}
