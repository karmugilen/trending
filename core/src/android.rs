//! JNI entry points. Names match the @JvmStatic methods on TrendingCore.

#![cfg(target_os = "android")]

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jdouble, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};

use crate::alerts::{self, AlertConfig};
use crate::{
    error_json, feed_for_slot, slot_feed_json, fmt_ago, fmt_bar_label, fmt_compact, gh_url, github_stamp, hn_url,
    mode_from, signals, source_from, store, trouble_message, window_json, FeedOptions, discussions_json, hn_discuss_url,
};

fn java_string(env: &mut JNIEnv, value: &JString) -> String {
    if value.is_null() {
        return String::new();
    }
    env.get_string(value).map(|text| text.into()).unwrap_or_default()
}

fn answer(env: &mut JNIEnv, value: String) -> jstring {
    match env.new_string(value) {
        Ok(text) => text.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn strings(json: &str) -> Vec<String> {
    serde_json::from_str(json).unwrap_or_default()
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_windowJson(
    mut env: JNIEnv,
    _class: JClass,
    now_sec: jlong,
    hours: jint,
) -> jstring {
    answer(&mut env, window_json(now_sec, hours))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_stamp(
    mut env: JNIEnv,
    _class: JClass,
    origin_sec: jlong,
) -> jstring {
    answer(&mut env, github_stamp(origin_sec))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_compact(
    mut env: JNIEnv,
    _class: JClass,
    n: jdouble,
) -> jstring {
    answer(&mut env, fmt_compact(n))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_ago(
    mut env: JNIEnv,
    _class: JClass,
    t: jlong,
    now_sec: jlong,
) -> jstring {
    answer(&mut env, fmt_ago(t, now_sec))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_barLabel(
    mut env: JNIEnv,
    _class: JClass,
    epoch_sec: jlong,
    hours: jint,
    offset_sec: jint,
) -> jstring {
    answer(&mut env, fmt_bar_label(epoch_sec, hours, offset_sec))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_troubleText(
    mut env: JNIEnv,
    _class: JClass,
    source: jint,
    code: jint,
) -> jstring {
    let http = if code < 0 { None } else { Some(code) };
    answer(&mut env, trouble_message(source_from(source), http))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_hnUrlNative(
    mut env: JNIEnv,
    _class: JClass,
    origin_sec: jlong,
    before_sec: jlong,
) -> jstring {
    answer(&mut env, hn_url(origin_sec, before_sec))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_ghUrlNative(
    mut env: JNIEnv,
    _class: JClass,
    kind: jint,
    origin_sec: jlong,
    keywords: JString,
) -> jstring {
    let keywords = strings(&java_string(&mut env, &keywords));
    answer(&mut env, gh_url(kind, origin_sec, &keywords))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_ingestJson(
    mut env: JNIEnv,
    _class: JClass,
    slot: JString,
    source: jint,
    origin_sec: jlong,
    now_sec: jlong,
    body: JString,
    append: jboolean,
) -> jstring {
    let slot = java_string(&mut env, &slot);
    let body = java_string(&mut env, &body);
    let json = match store::ingest(&slot, source_from(source), origin_sec, now_sec, &body, append != JNI_FALSE) {
        Ok(chunk) => serde_json::json!({
            "ok": true,
            "count": chunk.count,
            "oldest": chunk.oldest,
            "stored": chunk.stored,
        })
        .to_string(),
        Err(trouble) => error_json(&trouble),
    };
    answer(&mut env, json)
}

#[derive(serde::Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct OptsIn {
    hide_spam: bool,
    interests: Vec<String>,
    sensitivity: u8,
    query: String,
    limit: usize,
    topic: String,
    lang: String,
    bar: i32,
    only: u32,
    seen: Vec<String>,
    unread_only: bool,
}

impl Default for OptsIn {
    fn default() -> Self {
        OptsIn {
            hide_spam: false,
            interests: Vec::new(),
            sensitivity: 1,
            query: String::new(),
            limit: 0,
            topic: String::new(),
            lang: String::new(),
            bar: -1,
            only: 0,
            seen: Vec::new(),
            unread_only: false,
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_feedJson(
    mut env: JNIEnv,
    _class: JClass,
    slot: JString,
    mode: jint,
    hours: jint,
    now_sec: jlong,
    opts: JString,
) -> jstring {
    let slot = java_string(&mut env, &slot);
    let raw: OptsIn = serde_json::from_str(&java_string(&mut env, &opts)).unwrap_or_default();
    let opts = FeedOptions {
        hide_spam: raw.hide_spam,
        interests: signals::interests(&raw.interests),
        sensitivity: raw.sensitivity,
        query: crate::query_words(&raw.query),
        limit: if raw.limit == 0 { crate::SHOWN_DEFAULT } else { raw.limit },
        topic: signals::normalize(&raw.topic).trim().to_string(),
        lang: raw.lang,
        bar: raw.bar,
        only: raw.only,
        seen: raw.seen.into_iter().collect(),
        unread_only: raw.unread_only,
    };
    let json = match feed_for_slot(&slot, mode_from(mode), hours, now_sec, &opts) {
        Some(slot_feed) => slot_feed_json(&slot_feed),
        None => serde_json::json!({ "ok": false, "missing": true }).to_string(),
    };
    answer(&mut env, json)
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_slotInfoJson(
    mut env: JNIEnv,
    _class: JClass,
    slot: JString,
) -> jstring {
    let slot = java_string(&mut env, &slot);
    let json = store::with_slot(&slot, |snap| {
        serde_json::json!({ "ok": true, "fetched": snap.fetched, "origin": snap.origin, "count": snap.stories.len() })
    })
    .unwrap_or_else(|| serde_json::json!({ "ok": false }))
    .to_string();
    answer(&mut env, json)
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_exportSlot(
    mut env: JNIEnv,
    _class: JClass,
    slot: JString,
) -> jstring {
    let slot = java_string(&mut env, &slot);
    match store::export(&slot) {
        Some(json) => answer(&mut env, json),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_importSlot(
    mut env: JNIEnv,
    _class: JClass,
    slot: JString,
    json: JString,
) -> jboolean {
    let slot = java_string(&mut env, &slot);
    let json = java_string(&mut env, &json);
    if store::import(&slot, &json) { JNI_TRUE } else { JNI_FALSE }
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_alertsJson(
    mut env: JNIEnv,
    _class: JClass,
    slots: JString,
    config: JString,
    state: JString,
    now_sec: jlong,
) -> jstring {
    let slots = strings(&java_string(&mut env, &slots));
    let config: AlertConfig = serde_json::from_str(&java_string(&mut env, &config)).unwrap_or_default();
    let state = java_string(&mut env, &state);
    let outcome = alerts::check(&slots, &config, &state, now_sec);
    answer(&mut env, alerts::outcome_json(&outcome))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_discussUrlNative(
    mut env: JNIEnv,
    _class: JClass,
    link: JString,
) -> jstring {
    let link = java_string(&mut env, &link);
    answer(&mut env, hn_discuss_url(&link))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_discussionsJson(
    mut env: JNIEnv,
    _class: JClass,
    body: JString,
    skip_id: JString,
) -> jstring {
    let body = java_string(&mut env, &body);
    let skip_id = java_string(&mut env, &skip_id);
    answer(&mut env, discussions_json(&body, &skip_id))
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_parseStackJson(
    mut env: JNIEnv,
    _class: JClass,
    raw_content: JString,
) -> jstring {
    let raw = java_string(&mut env, &raw_content);
    let packages = signals::parse_stack_dependencies(&raw);
    let json = serde_json::to_string(&packages).unwrap_or_else(|_| "[]".to_string());
    answer(&mut env, json)
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_briefingNative(
    mut env: JNIEnv,
    _class: JClass,
    since_sec: jlong,
    now_sec: jlong,
    hn_slot: JString,
    gh_slot: JString,
    stack_json: JString,
    interests_json: JString,
    sensitivity: jint,
) -> jstring {
    let hn = java_string(&mut env, &hn_slot);
    let gh = java_string(&mut env, &gh_slot);
    let stack = java_string(&mut env, &stack_json);
    let interests = java_string(&mut env, &interests_json);
    let json = crate::briefing_json(
        since_sec,
        now_sec,
        &hn,
        &gh,
        &stack,
        &interests,
        (sensitivity.clamp(0, 2)) as u8,
    );
    answer(&mut env, json)
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_exportHistoryNative(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    answer(&mut env, store::export_history())
}

#[no_mangle]
pub extern "system" fn Java_app_omalauncher_trending_core_TrendingCore_importHistoryNative(
    mut env: JNIEnv,
    _class: JClass,
    json: JString,
) -> jboolean {
    let json = java_string(&mut env, &json);
    if store::import_history(&json) { JNI_TRUE } else { JNI_FALSE }
}
