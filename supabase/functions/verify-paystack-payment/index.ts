// Supabase Edge Function: verify-paystack-payment
// Verifies Paystack transaction server-side before updating invoice & payment state.

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const paystackSecretKey = Deno.env.get("PAYSTACK_SECRET_KEY")!;

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { reference } = await req.json();
    if (!reference) {
      return new Response(JSON.stringify({ error: "Payment reference is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const verifyResp = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(reference)}`, {
      headers: { Authorization: `Bearer ${paystackSecretKey}` },
    });
    const verifyJson = await verifyResp.json();

    if (!verifyResp.ok || !verifyJson.status || verifyJson.data?.status !== "success") {
      return new Response(
        JSON.stringify({
          verified: false,
          message: verifyJson.data?.gateway_response ?? verifyJson.message ?? "Payment not verified",
        }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const tx = verifyJson.data;
    const adminClient = createClient(supabaseUrl, serviceRoleKey);

    const { data: payment, error: payError } = await adminClient
      .from("payments")
      .select("id, invoice_id, student_id, amount_kobo, status")
      .eq("reference", reference)
      .single();

    if (payError || !payment) {
      return new Response(JSON.stringify({ error: "Pending payment record not found" }), {
        status: 404,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (payment.status === "verified") {
      return new Response(JSON.stringify({ verified: true, message: "Already verified", reference }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (tx.currency !== "NGN" || Number(tx.amount) < Number(payment.amount_kobo)) {
      return new Response(JSON.stringify({ verified: false, message: "Amount or currency mismatch" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    await adminClient
      .from("payments")
      .update({
        status: "verified",
        channel: tx.channel ?? "paystack",
        gateway_response: tx.gateway_response ?? "Approved",
        paid_at: tx.paid_at ?? new Date().toISOString(),
      })
      .eq("id", payment.id);

    const { data: invoice } = await adminClient
      .from("invoices")
      .select("amount_kobo, amount_paid_kobo")
      .eq("id", payment.invoice_id)
      .single();

    if (invoice) {
      const newPaid = Number(invoice.amount_paid_kobo) + Number(tx.amount);
      const newStatus = newPaid >= Number(invoice.amount_kobo) ? "paid" : "partial";
      await adminClient
        .from("invoices")
        .update({ amount_paid_kobo: newPaid, status: newStatus })
        .eq("id", payment.invoice_id);
    }

    await adminClient.from("audit_logs").insert({
      action: "PAYSTACK_PAYMENT_VERIFIED",
      entity_type: "payment",
      entity_id: payment.id,
      metadata: { reference, amount_kobo: tx.amount, channel: tx.channel },
    });

    return new Response(
      JSON.stringify({ verified: true, message: "Payment verified successfully", reference }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
