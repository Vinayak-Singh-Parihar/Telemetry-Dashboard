import { createClient } from "@supabase/supabase-js";

const supabaseUrl = "https://yunuilkqtczmlqhvdakz.supabase.co";
const supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl1bnVpbGtxdGN6bWxxaHZkYWt6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQwODc4MzUsImV4cCI6MjA4OTY2MzgzNX0.m1hJUfGIqUghhZE23U6i_XWCAPcxwuZ0ht-BYhb33u8";

export const supabase = createClient(supabaseUrl, supabaseKey);