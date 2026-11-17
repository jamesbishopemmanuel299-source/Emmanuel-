# GLA CBT – Green Light Academy

A production-ready Android School Management System and Computer-Based Testing (CBT) platform built with **Kotlin**, **Jetpack Compose (Material 3)**, **Supabase (PostgreSQL, Auth, Storage, Edge Functions)**, and **Paystack** for Nigerian school payments.

## Architecture & Tech Stack
- **Android UI**: Kotlin + Jetpack Compose + Material Design 3 with adaptive layouts (`NavigationBar` on phones, `NavigationRail` on tablets/foldables), custom typography (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`), and Light/Dark themes in Green, White, and Dark Navy.
- **Backend & Database**: Supabase PostgreSQL (22+ tables with Foreign Keys, Indexes, and Row Level Security), Supabase Auth, Supabase Storage, and Deno Edge Functions.
- **Payments**: Paystack NGN checkout initialized and cryptographically verified server-side via Supabase Edge Functions (`initialize-paystack-payment` and `verify-paystack-payment`).
- **Document Exports**: Native Android `PdfDocument` generation for Official Report Cards, Printable Student ID Cards, and Digital Payment Receipts, plus Excel-compatible CSV import/export via Storage Access Framework.

## Environment Variable Configuration (AI Studio Secrets Panel)
Configure the following keys in the **Secrets panel in AI Studio** (do not commit real secrets to `.env.example` or `local.properties`):

| Secret Key | Description |
| --- | --- |
| `SUPABASE_URL` | Your Supabase project URL (e.g. `https://xyzcompany.supabase.co`) |
| `SUPABASE_ANON_KEY` | Your Supabase project anonymous public key |
| `PAYSTACK_PUBLIC_KEY` | Your Paystack public key (`pk_test_...` or `pk_live_...`) |

### Supabase Edge Function Secrets
Set these in your Supabase project dashboard (`Project Settings -> Edge Functions -> Secrets`):
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `PAYSTACK_SECRET_KEY`

## Installation & Database Setup
1. **Run SQL Migrations**:
   - Open your Supabase SQL Editor or run Supabase CLI:
     ```bash
     supabase db push
     ```
   - Apply `supabase/migrations/001_initial_schema.sql` to create all 22+ tables, indexes, RLS policies, and Storage buckets.
2. **Deploy Edge Functions**:
   ```bash
   supabase functions deploy initialize-paystack-payment
   supabase functions deploy verify-paystack-payment
   ```
3. **Configure Secrets in AI Studio**:
   - Add `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `PAYSTACK_PUBLIC_KEY` in the AI Studio Secrets panel.
4. **Build & Run Android App**:
   - Rebuild the Android project; the Secrets Gradle Plugin injects values into `BuildConfig`.
