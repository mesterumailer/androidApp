const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function html(body, status = 200) {
  return new Response(body, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "content-security-policy": "default-src 'self'; img-src 'self' https: data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
    },
  });
}

const PUBLIC_ADS_BASE_URL = "https://sms-manager-ads.mesterumailer.workers.dev";
const MAX_IMAGE_BYTES = 500000;
const ALLOWED_IMAGE_TYPES = new Set(["image/webp", "image/png", "image/jpeg"]);

function validHttpsUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && Boolean(url.hostname);
  } catch {
    return false;
  }
}

function slugify(value) {
  return String(value || "").trim().toLowerCase();
}

function isValidSlug(value) {
  return /^[a-z0-9](?:[a-z0-9-]{1,78}[a-z0-9])?$/.test(value);
}

function toBase64(bytes) {
  let binary = "";
  const chunkSize = 32768;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

function validateImage(bytes, mime) {
  if (!ALLOWED_IMAGE_TYPES.has(mime)) return false;
  if (mime === "image/webp") {
    return bytes.length >= 12 &&
      String.fromCharCode(...bytes.subarray(0, 4)) === "RIFF" &&
      String.fromCharCode(...bytes.subarray(8, 12)) === "WEBP";
  }
  if (mime === "image/png") {
    const signature = [137, 80, 78, 71, 13, 10, 26, 10];
    return signature.every((value, index) => bytes[index] === value);
  }
  return bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
}

function parseCampaign(form, requireImage) {
  const slug = slugify(form.get("slug"));
  const title = String(form.get("title") || "").trim();
  const description = String(form.get("description") || "").trim();
  const ctaText = String(form.get("cta_text") || "مشاهده").trim();
  const targetUrl = String(form.get("target_url") || "").trim();
  const weight = Number(form.get("weight") || 1);
  const priority = Number(form.get("priority") || 100);
  const startsAtRaw = String(form.get("starts_at") || "").trim();
  const endsAtRaw = String(form.get("ends_at") || "").trim();
  const startsAt = startsAtRaw ? new Date(startsAtRaw) : null;
  const endsAt = endsAtRaw ? new Date(endsAtRaw) : null;
  const isActive = form.get("is_active") === "on" || form.get("is_active") === "true";

  if (!isValidSlug(slug)) {
    throw new Error("شناسه باید انگلیسی و شامل حروف کوچک، عدد و خط تیره باشد.");
  }
  if (!title || title.length > 120) throw new Error("عنوان تبلیغ الزامی و حداکثر ۱۲۰ نویسه است.");
  if (description.length > 300) throw new Error("توضیحات حداکثر ۳۰۰ نویسه است.");
  if (!ctaText || ctaText.length > 40) throw new Error("متن دکمه معتبر نیست.");
  if (!validHttpsUrl(targetUrl)) throw new Error("لینک مقصد باید HTTPS باشد.");
  if (!Number.isInteger(weight) || weight < 1 || weight > 1000) {
    throw new Error("وزن نمایش باید عدد صحیح بین ۱ تا ۱۰۰۰ باشد.");
  }
  if (!Number.isInteger(priority) || priority < 0 || priority > 1000) {
    throw new Error("اولویت باید عدد صحیح بین صفر تا ۱۰۰۰ باشد.");
  }
  if (startsAt && !Number.isFinite(startsAt.getTime())) throw new Error("تاریخ شروع معتبر نیست.");
  if (endsAt && !Number.isFinite(endsAt.getTime())) throw new Error("تاریخ پایان معتبر نیست.");
  if (startsAt && endsAt && endsAt <= startsAt) throw new Error("تاریخ پایان باید بعد از تاریخ شروع باشد.");

  const file = form.get("image");
  const hasFile = file && typeof file.arrayBuffer === "function" && file.size > 0;
  if (requireImage && !hasFile) throw new Error("برای تبلیغ جدید، تصویر بنر لازم است.");

  return {
    slug, title, description, ctaText, targetUrl, weight, priority, isActive,
    startsAt: startsAt ? startsAt.toISOString().slice(0, 19).replace("T", " ") : null,
    endsAt: endsAt ? endsAt.toISOString().slice(0, 19).replace("T", " ") : null,
    file: hasFile ? file : null,
  };
}

async function readImage(file) {
  if (!file) return null;
  if (file.size > MAX_IMAGE_BYTES) throw new Error("حجم تصویر باید حداکثر ۵۰۰ کیلوبایت باشد.");
  if (!ALLOWED_IMAGE_TYPES.has(file.type)) throw new Error("فرمت تصویر باید WebP، PNG یا JPEG باشد.");
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (!validateImage(bytes, file.type)) throw new Error("محتوای تصویر با فرمت اعلام‌شده مطابقت ندارد.");
  return { mime: file.type, base64: toBase64(bytes) };
}

function campaignRow(row) {
  const imageUrl = row.image_url && String(row.image_url).startsWith("https://")
    ? row.image_url
    : "/api/admin/ads/" + row.id + "/image";
  return {
    id: row.id,
    slug: row.slug,
    placement: row.placement,
    title: row.title,
    description: row.description || "",
    cta_text: row.cta_text || "مشاهده",
    target_url: row.target_url,
    is_active: Number(row.is_active) === 1,
    weight: Number(row.weight) || 1,
    priority: Number(row.priority) || 0,
    impressions: Number(row.impressions) || 0,
    click_count: Number(row.click_count) || 0,
    image_url: imageUrl,
    starts_at: row.starts_at || "",
    ends_at: row.ends_at || "",
    updated_at: row.updated_at,
  };
}

const ADMIN_HTML = [
'<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">',
'<meta name="viewport" content="width=device-width, initial-scale=1">',
'<title>مدیریت تبلیغات پیامک‌یار</title>',
'<style>',
':root{color-scheme:light;--bg:#f3f5f8;--card:#fff;--text:#16202e;--muted:#677487;--line:#e3e8ef;--accent:#315fbd;--green:#168451;--red:#bc3b43}',
'*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:Tahoma,Arial,sans-serif}',
'header{background:#17243b;color:white;padding:22px max(20px,calc((100% - 1120px)/2));display:flex;justify-content:space-between;align-items:center;gap:16px}',
'h1{font-size:21px;margin:0}header p{margin:7px 0 0;color:#c6d1e2;font-size:12px}.shield{font-size:12px;border:1px solid #52627d;border-radius:30px;padding:8px 12px;white-space:nowrap}',
'main{max-width:1120px;margin:22px auto;padding:0 18px}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:18px}',
'.stat,.panel,.campaign{background:var(--card);border:1px solid var(--line);border-radius:14px}.stat{padding:16px}.stat small{display:block;color:var(--muted);font-size:12px}.stat strong{display:block;font-size:25px;margin-top:8px}',
'.layout{display:grid;grid-template-columns:minmax(280px,360px) 1fr;gap:16px;align-items:start}.panel{padding:18px}.panel h2{font-size:16px;margin:0 0 15px}',
'.field{margin-bottom:12px}.field label{display:block;font-size:12px;color:#445166;margin-bottom:6px}.field input,.field textarea{width:100%;border:1px solid #d8e0ea;border-radius:9px;padding:10px;font:inherit;font-size:13px;background:white;color:var(--text)}.field textarea{min-height:70px;resize:vertical}',
'.two{display:grid;grid-template-columns:1fr 1fr;gap:10px}.check{display:flex;align-items:center;gap:8px;font-size:13px;margin:8px 0 14px}',
'.buttons{display:flex;gap:8px;flex-wrap:wrap}.btn{border:0;border-radius:9px;padding:10px 14px;background:var(--accent);color:white;font:inherit;font-size:12px;cursor:pointer}.btn.secondary{background:#edf1f6;color:#263449}.btn.danger{background:#fff0f0;color:var(--red)}.btn:disabled{opacity:.5;cursor:wait}',
'.hint{font-size:11px;color:var(--muted);line-height:1.8}.list{display:grid;gap:12px}.campaign{padding:13px;display:grid;grid-template-columns:120px 1fr;gap:13px}',
'.campaign img{width:120px;height:88px;object-fit:cover;border-radius:9px;background:#eef1f5}.campaign h3{font-size:14px;margin:0 0 5px}.meta{font-size:11px;color:var(--muted);line-height:1.9}',
'.status{display:inline-block;border-radius:30px;padding:3px 8px;font-size:10px;background:#e7f7ee;color:var(--green);margin-inline-start:6px}.status.off{background:#edf0f4;color:#667085}',
'.actions{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}.actions .btn{padding:7px 10px}.notice{padding:10px 12px;border-radius:9px;background:#edf4ff;color:#274d8e;font-size:12px;margin-bottom:14px;display:none}.notice.error{background:#fff0f0;color:var(--red)}',
'.empty{background:white;border:1px dashed #cad3df;border-radius:12px;padding:28px;text-align:center;color:var(--muted);font-size:13px}',
'@media(max-width:800px){.layout{grid-template-columns:1fr}.stats{gap:8px}.stat{padding:12px}.stat strong{font-size:21px}}@media(max-width:460px){header{align-items:flex-start;flex-direction:column}.campaign{grid-template-columns:90px 1fr}.campaign img{width:90px;height:75px}.two{grid-template-columns:1fr}}',
'</style></head><body>',
'<header><div><h1>مدیریت تبلیغات پیامک‌یار</h1><p>مدیریت بنرها، وزن نمایش و عملکرد کمپین‌ها</p></div><div class="shield">محافظت‌شده با Cloudflare Access</div></header>',
'<main><div id="notice" class="notice"></div>',
'<section class="stats"><div class="stat"><small>تبلیغ فعال</small><strong id="activeCount">—</strong></div><div class="stat"><small>دفعات انتخاب بنر برای نمایش</small><strong id="impressions">—</strong></div><div class="stat"><small>تعداد کلیک</small><strong id="clicks">—</strong></div></section>',
'<div class="layout"><section class="panel"><h2 id="formTitle">افزودن تبلیغ جدید</h2>',
'<form id="campaignForm"><input type="hidden" name="id" id="campaignId">',
'<div class="field"><label for="slug">شناسه یکتا (انگلیسی)</label><input id="slug" name="slug" required maxlength="80" placeholder="campaign-name"></div>',
'<div class="field"><label for="title">عنوان تبلیغ</label><input id="title" name="title" required maxlength="120"></div>',
'<div class="field"><label for="description">توضیحات</label><textarea id="description" name="description" maxlength="300"></textarea></div>',
'<div class="field"><label for="cta_text">متن دکمه</label><input id="cta_text" name="cta_text" value="مشاهده سایت" maxlength="40"></div>',
'<div class="field"><label for="target_url">لینک مقصد (HTTPS)</label><input id="target_url" name="target_url" type="url" required placeholder="https://example.com"></div>',
'<div class="two"><div class="field"><label for="weight">وزن نمایش (۱ تا ۱۰۰۰)</label><input id="weight" name="weight" type="number" min="1" max="1000" value="1" required></div>',
'<div class="field"><label for="priority">اولویت ترتیب (۰ تا ۱۰۰۰)</label><input id="priority" name="priority" type="number" min="0" max="1000" value="100" required></div></div>',
'<div class="two"><div class="field"><label for="starts_at">شروع نمایش (اختیاری)</label><input id="starts_at" name="starts_at" type="datetime-local"></div>',
'<div class="field"><label for="ends_at">پایان نمایش (اختیاری)</label><input id="ends_at" name="ends_at" type="datetime-local"></div></div>',
'<div class="field"><label for="image">تصویر بنر (WebP / PNG / JPEG، حداکثر ۵۰۰ KB)</label><input id="image" name="image" type="file" accept="image/webp,image/png,image/jpeg"></div>',
'<label class="check"><input id="is_active" name="is_active" type="checkbox" checked> تبلیغ فعال باشد</label>',
'<div class="buttons"><button class="btn" id="saveButton" type="submit">ذخیره تبلیغ</button><button class="btn secondary" type="button" id="resetButton">پاک کردن فرم</button></div>',
'<p class="hint">تبلیغ یجینگ چاینیز از قبل ثبت شده و وزن پیش‌فرض آن ۳ است. وزن ۳ در برابر وزن ۱ یعنی در هر چهار انتخاب، سه بار تبلیغ اول و یک بار تبلیغ دوم انتخاب می‌شود. تبلیغات غیرفعال در چرخش شرکت نمی‌کنند.</p>',
'</form></section><section><div class="panel" style="margin-bottom:12px"><h2>فهرست کمپین‌ها</h2><p class="hint">چرخش در هر درخواست بنر از طرف اجرای تازهٔ اپ انجام می‌شود. آمار نمایش، تعداد انتخاب‌های موفق بنر از سرویس را نشان می‌دهد.</p></div>',
'<div id="campaignList" class="list"><div class="empty">در حال بارگذاری تبلیغات…</div></div></section></div></main>',
'<script>',
'const $=id=>document.getElementById(id);let campaigns=[];',
'function notify(message,error=false){const n=$("notice");n.textContent=message;n.className="notice"+(error?" error":"");n.style.display="block";window.scrollTo({top:0,behavior:"smooth"});setTimeout(()=>n.style.display="none",5000)}',
'function esc(v){return String(v??"").replace(/[&<>"]/g,c=>c.charCodeAt(0)===34?"&quot;":({"&":"&amp;","<":"&lt;",">":"&gt;"}[c]))}',
'async function api(url,options={}){const r=await fetch(url,{...options,credentials:"same-origin"});const data=await r.json().catch(()=>({error:"پاسخ نامعتبر از سرور"}));if(!r.ok)throw new Error(data.error||"خطا در ارتباط با سرور");return data}',
'function draw(){const active=campaigns.filter(a=>a.is_active);$("activeCount").textContent=active.length;$("impressions").textContent=campaigns.reduce((s,a)=>s+a.impressions,0).toLocaleString("fa-IR");$("clicks").textContent=campaigns.reduce((s,a)=>s+a.click_count,0).toLocaleString("fa-IR");',
'if(!campaigns.length){$("campaignList").innerHTML="<div class=\\"empty\\">هنوز تبلیغی ثبت نشده است.</div>";return}',
'$("campaignList").innerHTML=campaigns.map(a=>"<article class=\\"campaign\\"><img src=\\""+esc(a.image_url)+"\\" alt=\\""+esc(a.title)+"\\" loading=\\"lazy\\"><div><h3>"+esc(a.title)+"<span class=\\"status "+(a.is_active?"":"off")+"\\">"+(a.is_active?"فعال":"غیرفعال")+"</span></h3><div class=\\"meta\\">شناسه: "+esc(a.slug)+"<br>وزن: "+a.weight+" · اولویت: "+a.priority+"<br>نمایش: "+a.impressions.toLocaleString("fa-IR")+" · کلیک: "+a.click_count.toLocaleString("fa-IR")+"</div><div class=\\"actions\\"><button class=\\"btn secondary\\" data-edit=\\""+a.id+"\\">ویرایش</button><button class=\\"btn "+(a.is_active?"danger":"secondary")+"\\" data-toggle=\\""+a.id+"\\">"+(a.is_active?"غیرفعال‌سازی":"فعال‌سازی")+"</button><button class=\\"btn danger\\" data-delete=\\""+a.id+"\\">حذف</button></div></div></article>").join("")}',
'async function refresh(){const r=await api("/api/admin/ads");campaigns=r.ads||[];draw()}',
'function setDateInput(id,value){if(!value){$(id).value="";return}const d=new Date(value.replace(" ","T")+"Z");if(!Number.isFinite(d.getTime())){$(id).value="";return}const local=new Date(d.getTime()-d.getTimezoneOffset()*60000);$(id).value=local.toISOString().slice(0,16)}',
'function resetForm(){$("campaignForm").reset();$("campaignId").value="";$("weight").value="1";$("priority").value="100";$("is_active").checked=true;setDateInput("starts_at","");setDateInput("ends_at","");$("formTitle").textContent="افزودن تبلیغ جدید";$("saveButton").textContent="ذخیره تبلیغ";$("image").required=true}',
'function editCampaign(id){const a=campaigns.find(x=>x.id===Number(id));if(!a)return;$("campaignId").value=a.id;$("slug").value=a.slug;$("slug").readOnly=true;$("title").value=a.title;$("description").value=a.description;$("cta_text").value=a.cta_text;$("target_url").value=a.target_url;$("weight").value=a.weight;$("priority").value=a.priority;$("is_active").checked=a.is_active;setDateInput("starts_at",a.starts_at);setDateInput("ends_at",a.ends_at);$("image").required=false;$("formTitle").textContent="ویرایش تبلیغ";$("saveButton").textContent="ذخیره تغییرات";window.scrollTo({top:0,behavior:"smooth"})}',
'$("campaignForm").addEventListener("submit",async e=>{e.preventDefault();const id=$("campaignId").value;const data=new FormData(e.currentTarget);data.set("is_active",$("is_active").checked?"on":"off");const start=$("starts_at").value;const end=$("ends_at").value;data.set("starts_at",start?new Date(start).toISOString():"");data.set("ends_at",end?new Date(end).toISOString():"");$("saveButton").disabled=true;try{await api(id?"/api/admin/ads/"+id:"/api/admin/ads",{method:id?"PUT":"POST",body:data});notify(id?"تغییرات تبلیغ ذخیره شد.":"تبلیغ جدید اضافه شد.");resetForm();$("slug").readOnly=false;await refresh()}catch(err){notify(err.message,true)}finally{$("saveButton").disabled=false}});',
'$("resetButton").addEventListener("click",()=>{$("slug").readOnly=false;resetForm()});',
'$("campaignList").addEventListener("click",async e=>{const edit=e.target.closest("[data-edit]"),toggle=e.target.closest("[data-toggle]"),del=e.target.closest("[data-delete]");if(edit){editCampaign(edit.dataset.edit);return}if(toggle){const a=campaigns.find(x=>x.id===Number(toggle.dataset.toggle));if(!a)return;try{await api("/api/admin/ads/"+a.id+"/status",{method:"PATCH",headers:{"content-type":"application/json"},body:JSON.stringify({is_active:!a.is_active})});await refresh();notify("وضعیت تبلیغ به‌روزرسانی شد.")}catch(err){notify(err.message,true)}return}if(del){const a=campaigns.find(x=>x.id===Number(del.dataset.delete));if(!a||!confirm("تبلیغ «"+a.title+"» برای همیشه حذف شود؟"))return;try{await api("/api/admin/ads/"+a.id,{method:"DELETE"});await refresh();notify("تبلیغ حذف شد.")}catch(err){notify(err.message,true)}}});',
'resetForm();$("slug").readOnly=false;refresh().catch(err=>notify(err.message,true));',
'</script></body></html>'
].join("");

async function requireAccess(ctx) {
  if (!ctx || !ctx.access || typeof ctx.access.getIdentity !== "function") return null;
  try {
    const identity = await ctx.access.getIdentity();
    return identity && identity.email ? identity : null;
  } catch {
    return null;
  }
}

async function listAds(db) {
  const result = await db.prepare(
    "SELECT id, slug, placement, title, description, cta_text, target_url, is_active, priority, weight, impressions, click_count, image_url, starts_at, ends_at, updated_at FROM ads ORDER BY is_active DESC, priority DESC, id ASC"
  ).all();
  return (result && result.results ? result.results : []).map(campaignRow);
}

async function createAd(db, form) {
  const campaign = parseCampaign(form, true);
  const image = await readImage(campaign.file);
  await db.prepare(
    "INSERT INTO ads (slug, placement, title, description, cta_text, image_mime, image_base64, image_url, target_url, is_active, priority, weight, starts_at, ends_at) VALUES (?, 'home_bottom', ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)"
  ).bind(
    campaign.slug, campaign.title, campaign.description, campaign.ctaText,
    image.mime, image.base64, campaign.targetUrl, campaign.isActive ? 1 : 0,
    campaign.priority, campaign.weight, campaign.startsAt, campaign.endsAt
  ).run();
}

async function updateAd(db, id, form) {
  const campaign = parseCampaign(form, false);
  const image = await readImage(campaign.file);
  const result = await db.prepare(
    "UPDATE ads SET title = ?, description = ?, cta_text = ?, target_url = ?, is_active = ?, priority = ?, weight = ?, starts_at = ?, ends_at = ?, image_mime = COALESCE(?, image_mime), image_base64 = COALESCE(?, image_base64), image_url = CASE WHEN ? = 1 THEN NULL ELSE image_url END, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND slug = ?"
  ).bind(
    campaign.title, campaign.description, campaign.ctaText, campaign.targetUrl,
    campaign.isActive ? 1 : 0, campaign.priority, campaign.weight, campaign.startsAt, campaign.endsAt,
    image ? image.mime : null, image ? image.base64 : null, image ? 1 : 0, id, campaign.slug
  ).run();
  if (result && result.meta && result.meta.changes === 0) throw new Error("تبلیغ موردنظر پیدا نشد.");
}

export default {
  async fetch(request, env, ctx) {
    const identity = await requireAccess(ctx);
    if (!identity) return json({ ok: false, error: "Cloudflare Access authentication required" }, 403);
    if (!env || !env.DB) return json({ ok: false, error: "database_binding_missing" }, 503);

    const url = new URL(request.url);
    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/admin")) {
      return html(ADMIN_HTML);
    }

    if (request.method === "GET" && url.pathname === "/api/admin/ads") {
      try {
        return json({ ads: await listAds(env.DB) });
      } catch (error) {
        console.error("Ads admin list failure:", error);
        return json({ error: "خواندن فهرست تبلیغات از D1 ممکن نشد." }, 500);
      }
    }

    if (request.method === "POST" && url.pathname === "/api/admin/ads") {
      try {
        const form = await request.formData();
        await createAd(env.DB, form);
        return json({ ok: true }, 201);
      } catch (error) {
        const message = String(error && error.message ? error.message : "");
        const duplicate = message.toLowerCase().includes("unique");
        return json({ error: duplicate ? "شناسهٔ این تبلیغ قبلاً ثبت شده است." : (message || "ثبت تبلیغ انجام نشد.") }, duplicate ? 409 : 400);
      }
    }

    const imageMatch = url.pathname.match(/^\/api\/admin\/ads\/(\d+)\/image$/);
    if (imageMatch && request.method === "GET") {
      try {
        const row = await env.DB.prepare(
          "SELECT image_mime, image_base64, image_url FROM ads WHERE id = ?"
        ).bind(Number(imageMatch[1])).first();
        if (!row) return new Response("Not found", { status: 404 });
        if (row.image_url && String(row.image_url).startsWith("https://")) {
          return Response.redirect(row.image_url, 302);
        }
        if (!row.image_base64) return new Response("Image not found", { status: 404 });
        const binary = atob(row.image_base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
        return new Response(bytes, { headers: {
          "content-type": row.image_mime || "image/webp",
          "cache-control": "private, no-store",
          "x-content-type-options": "nosniff"
        }});
      } catch {
        return new Response("Image unavailable", { status: 500 });
      }
    }

    const statusMatch = url.pathname.match(/^\/api\/admin\/ads\/(\d+)\/status$/);
    if (statusMatch && request.method === "PATCH") {
      const adId = Number(statusMatch[1]);
      try {
        const body = await request.json();
        if (typeof body.is_active !== "boolean") return json({ error: "وضعیت فعال‌بودن معتبر نیست." }, 400);
        const result = await env.DB.prepare(
          "UPDATE ads SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        ).bind(body.is_active ? 1 : 0, adId).run();
        if (result && result.meta && result.meta.changes === 0) return json({ error: "تبلیغ پیدا نشد." }, 404);
        return json({ ok: true });
      } catch {
        return json({ error: "تغییر وضعیت تبلیغ انجام نشد." }, 400);
      }
    }

    const adMatch = url.pathname.match(/^\/api\/admin\/ads\/(\d+)$/);
    if (adMatch && request.method === "PUT") {
      try {
        const form = await request.formData();
        await updateAd(env.DB, Number(adMatch[1]), form);
        return json({ ok: true });
      } catch (error) {
        const message = String(error && error.message ? error.message : "");
        const duplicate = message.toLowerCase().includes("unique");
        return json({ error: duplicate ? "شناسهٔ این تبلیغ قبلاً ثبت شده است." : (message || "ویرایش تبلیغ انجام نشد.") }, duplicate ? 409 : 400);
      }
    }

    if (adMatch && request.method === "DELETE") {
      try {
        const result = await env.DB.prepare("DELETE FROM ads WHERE id = ?").bind(Number(adMatch[1])).run();
        if (result && result.meta && result.meta.changes === 0) return json({ error: "تبلیغ پیدا نشد." }, 404);
        return json({ ok: true });
      } catch {
        return json({ error: "حذف تبلیغ انجام نشد." }, 500);
      }
    }

    return json({ error: "not_found" }, 404);
  },
};
