#!/usr/bin/env python3
"""
Сборка brp_assistant.db из каталогов 2026 и карточек знаний.
Запуск: python3 tools/build_database.py
Результат: app/src/main/assets/brp_assistant.db
"""

import os
import re
import json
import sqlite3
from pathlib import Path

DB_OUTPUT = "../app/src/main/assets/brp_assistant.db"
KNOWLEDGE_DIR = "../app/src/main/assets/knowledge"
UPLOADS_DIR = "../../uploads"

# ============================================================
# MODELS DATA (из каталогов 2026)
# ============================================================
MODELS = [
    # --- Can-Am ATV ---
    ("can-am-outlander-450-2026", "can-am-atv", "atv", "utility", 2026, "Outlander 450", "G2", "Rotax 450", "4-stroke", 450, 38, "CVT", "2WD/4WD", '["DPS","HMWPE skid plate","LinQ"]', '[]', '["Viper Red"]', 7499, 0, "Базовый ATV"),
    ("can-am-outlander-570-2026", "can-am-atv", "atv", "utility", 2026, "Outlander 570", "G2", "Rotax 570", "4-stroke", 570, 48, "CVT", "2WD/4WD", '["DPS","LinQ","650W magneto"]', '[]', '["Viper Red","Can-Am Red"]', 8499, 0, "Бестселлер ATV"),
    ("can-am-outlander-650-2026", "can-am-atv", "atv", "utility", 2026, "Outlander 650", "G3", "Rotax 650", "4-stroke", 650, 62, "CVT", "2WD/4WD selectable", '["DPS","Smart-Lok","10.8 дюйма front travel","LED lights"]', '["REV Gen5 platform"]', '["Desert Tan","Can-Am Red"]', 10499, 0, "Новое поколение G3"),
    ("can-am-outlander-850-xt-2026", "can-am-atv", "atv", "utility", 2026, "Outlander 850 XT", "G3", "Rotax 850", "4-stroke", 850, 78, "CVT", "2WD/4WD selectable", '["XT package","winch","Smart-Lok","heavy-duty bumpers"]', '["REV Gen5 platform"]', '["Desert Tan","Black"]', 11999, 0, "Универсальный ATV"),
    ("can-am-outlander-xtp-1000r-2026", "can-am-atv", "atv", "utility", 2026, "Outlander XT-P 1000R T ABS", "G3", "Rotax 1000R", "4-stroke", 976, 91, "CVT", "2WD/4WD selectable", '["ABS","Smart-Lok","XT-P package","aluminum wheels"]', '["Smart-Shox SAS option"]', '["Can-Am Red"]', 15999, 0, "Топовый ATV"),
    ("can-am-outlander-6x6-2026", "can-am-atv", "atv", "utility", 2026, "Outlander 6x6", "G3L", "Rotax 850/1000R", "4-stroke", 850, 78, "CVT", "6x6 selectable", '["6 wheels","massive payload","flatbed"]', '["NEW 2026"]', '["Black"]', None, 0, "Шестиколёсный — новинка 2026"),
    ("can-am-outlander-electric-2026", "can-am-atv", "atv", "utility", 2026, "Outlander Electric", "G3", "Rotax E-Power", "electric", None, 47, "CVT", "2WD/4WD selectable", '["Electric powertrain","50mi range","fast charging","quiet"]', '["NEW 2026 — первый электро ATV"]', '["White"]', None, 1, "Первый электро-ATV Can-Am"),
    ("can-am-outlander-xmr-850-2026", "can-am-atv", "atv", "sport", 2026, "Outlander X mr 850", "G3", "Rotax 850", "4-stroke", 850, 78, "CVT", "2WD/4WD", '["30 дюйма XPS Swamp King XL","snorkel","high bumper","winch"]', '[]', '["Manta Green"]', None, 0, "Муд-ATV"),
    ("can-am-renegade-xxc-650-2026", "can-am-atv", "atv", "sport", 2026, "Renegade X XC 650 T ABS", "G2S", "Rotax 650", "4-stroke", 650, 62, "CVT", "2WD/4WD", '["Sport package","ABS","performance shocks"]', '[]', '["Black"]', None, 0, "Спорт-квадро"),
    # --- Can-Am SSV ---
    ("can-am-traxter-hd9-2026", "can-am-ssv", "ssv", "utility", 2026, "Traxter HD9", "G1", "Rotax HD9", "4-stroke", 899, 72, "CVT", "4WD", '["Towing 2500 lb","cargo box","work-ready"]', '[]', '["Desert Tan"]', 12499, 0, "Рабочий SSV"),
    ("can-am-traxter-hd11-2026", "can-am-ssv", "ssv", "utility", 2026, "Traxter HD11", "G2", "Rotax HD11", "4-stroke", 1087, 92, "CVT", "4WD", '["NEW G2 platform","larger cab","HVAC option"]', '["NEW 2026"]', '["Stealth Black"]', None, 0, "Новое поколение Traxter"),
    ("can-am-defender-2026", "can-am-ssv", "ssv", "utility", 2026, "Defender", "defender", "Rotax HD9/HD10", "4-stroke", 976, 82, "CVT", "4WD", '["Class-leading towing","200+ accessories","LinQ"]', '["Updated 2026 cab","10.25 дюйма display Limited"]', '["Desert Tan","Black"]', 13399, 0, "Универсальный SSV"),
    ("can-am-defender-6x6-xt-2026", "can-am-ssv", "ssv", "utility", 2026, "Defender 6x6 XT", "defender", "Rotax HD10", "4-stroke", 976, 82, "CVT", "6x6", '["6 wheels","massive cargo","winch"]', '[]', '["Black"]', 25399, 0, "6-колёсный Defender"),
    ("can-am-defender-limited-2026", "can-am-ssv", "ssv", "utility", 2026, "Defender Limited", "defender", "Rotax HD10", "4-stroke", 976, 82, "CVT", "4WD", '["HVAC","10.25 дюйма touchscreen","BRP GO!","premium audio"]', '["Updated 2026 cab"]', '["Desert Tan"]', 34299, 0, "Премиум SSV с кабиной"),
    ("can-am-maverick-r-2026", "can-am-ssv", "ssv", "sport", 2026, "Maverick R", "maverick-r", "Rotax 240 HP", "turbo-4stroke", None, 240, "DCT 7-speed", "4WD", '["240 HP","Dual-clutch","Smart-Shox","tall-knuckle suspension","10.25 дюйма display"]', '[]', '["Can-Am Red","Black"]', 34999, 0, "240 HP спорт SSV с DCT"),
    ("can-am-maverick-r-max-2026", "can-am-ssv", "ssv", "sport", 2026, "Maverick R Max", "maverick-r", "Rotax 240 HP", "turbo-4stroke", None, 240, "DCT 7-speed", "4WD", '["4-seater","240 HP","DCT","spacious rear seats"]', '[]', '["Black"]', None, 0, "4-местный Maverick R"),
    ("can-am-maverick-r-xrc-2026", "can-am-ssv", "ssv", "sport", 2026, "Maverick R X rc", "maverick-r", "Rotax 240 HP", "turbo-4stroke", None, 240, "DCT+ROCK", "4WD", '["Rock crawler","Extra Low gear","35 дюйма tires","FOX steering damper","skid plates"]', '["NEW 2026"]', '["Black"]', None, 0, "Скальный краулер — NEW 2026"),
    ("can-am-maverick-x3-2026", "can-am-ssv", "ssv", "sport", 2026, "Maverick X3", "maverick-x3", "Rotax 172 HP", "turbo-4stroke", None, 172, "CVT", "4WD", '["Turbo","Smart-Shox option","FOX shocks"]', '[]', '["Red","Black"]', None, 0, "Классический спорт SSV"),
    # --- Can-Am 3-Wheel ---
    ("can-am-ryker-600-2026", "can-am-3wheel", "3-wheel", "recreation", 2026, "Ryker 600", "ryker", "Rotax 600 ACE", "4-stroke", 600, 50, "CVT", "RWD", '["UFit","VSS","Customizable panels"]', '[]', '["Classic panels included"]', None, 0, "Доступный 3-wheel"),
    ("can-am-ryker-rally-2026", "can-am-3wheel", "3-wheel", "sport", 2026, "Ryker Rally 900", "ryker", "Rotax 900 ACE", "4-stroke", 900, 82, "CVT", "RWD", '["Rally mode","Akrapovic exhaust","skid plate","handguards","KYB HPG shocks"]', '[]', '["Multiple"]', None, 0, "Rally 3-wheel"),
    ("can-am-spyder-f3-s-2026", "can-am-3wheel", "3-wheel", "touring", 2026, "Spyder F3-S", "spyder", "Rotax 1330 ACE", "4-stroke", 1330, 115, "Semi-Auto/Manual", "RWD", '["115 HP","10.25 дюйма touchscreen","BRP Connect","Apple CarPlay","Sport mode"]', '["Circuit Yellow Metallic"]', '["Monolith Black Satin","Circuit Yellow"]', None, 0, "Cruiser 3-wheel"),
    ("can-am-spyder-f3-limited-2026", "can-am-3wheel", "3-wheel", "touring", 2026, "Spyder F3 Limited", "spyder", "Rotax 1330 ACE", "4-stroke", 1330, 115, "Semi-Auto", "RWD", '["Heated grips","Top case 60L","Audio Premium","Air suspension","passenger footboards"]', '[]', '["Pearl White"]', None, 0, "Premium cruiser"),
    ("can-am-canyon-2026", "can-am-3wheel", "3-wheel", "touring", 2026, "Canyon", "canyon", "Rotax 1330 ACE", "4-stroke", 1330, 115, "Semi-Auto", "RWD", '["Adventure 3-wheel","new platform"]', '["NEW 2026"]', '["Multiple"]', None, 0, "NEW — Adventure 3-wheel"),
    # --- Ski-Doo ---
    ("skidoo-mxz-xrs-2026", "ski-doo", "snowmobile", "trail", 2026, "MXZ X-RS", "REV Gen5", "850 E-TEC Turbo R / 850 E-TEC", "2-stroke", 849, 165, "CVT", "RWD", '["RAS RX front","rMotion X rear","10.25 дюйма display","KYB Pro shocks","Premium LED"]', '["Mineral Blue Premium"]', '["Mineral Blue","Black"]', None, 0, "Флагман Trail"),
    ("skidoo-mxz-sport-2026", "ski-doo", "snowmobile", "trail", 2026, "MXZ Sport", "REV Gen5", "600 EFI", "2-stroke", 599, 85, "CVT", "RWD", '["4.5 дюйма display","electric start","LED headlights"]', '["REV Gen5 narrow"]', '["Neo Yellow"]', None, 0, "Бюджетный trail"),
    ("skidoo-renegade-xrs-2026", "ski-doo", "snowmobile", "trail", 2026, "Renegade X-RS", "REV Gen5", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["RAS RX","Smart-Shox","heated seat","10.25 дюйма display"]', '["RAS RX front"]', '["Mineral Blue","Black"]', None, 0, "Топ crossover"),
    ("skidoo-summit-x-2026", "ski-doo", "snowmobile", "mountain", 2026, "Summit X", "REV Gen5 LW", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["32 дюйма ski stance","tMotion XT","PowderMax X-Light track","10.25 дюйма display"]', '["REV Gen5 Lightweight"]', '["Monument Grey","Terra Green"]', None, 0, "Лёгкий горный"),
    ("skidoo-summit-adrenaline-2026", "ski-doo", "snowmobile", "mountain", 2026, "Summit Adrenaline", "REV Gen5", "850 E-TEC", "2-stroke", 849, 165, "CVT", "RWD", '["tMotion X","Pilot DS 3 skis","LED headlights"]', '[]', '["Catalyst Grey"]', None, 0, "Горный универсал"),
    ("skidoo-freeride-2026", "ski-doo", "snowmobile", "mountain", 2026, "Freeride", "REV Gen5 LW", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["tMotion XT rigid","E-TEC SHOT","reinforced rails","10.25 дюйма display"]', '["REV Gen5 LW"]', '["Scandi Blue"]', None, 0, "Extreme deep snow"),
    ("skidoo-backcountry-xrs-2026", "ski-doo", "snowmobile", "crossover", 2026, "Backcountry X-RS", "REV Gen5", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["KYB Pro shocks","39 or 43 дюйма stance","4 track options","10.25 дюйма display"]', '["Scandi Blue Premium"]', '["Scandi Blue","Black"]', None, 0, "Агрессивный crossover"),
    ("skidoo-expedition-xtreme-2026", "ski-doo", "snowmobile", "utility", 2026, "Expedition Xtreme", "REV Gen5 SU", "850 E-TEC / 900 ACE Turbo R", "2/4-stroke", 849, 165, "CVT", "RWD", '["uMotion","Multi-LinQ 20"","KYB Pro 36","air radiator"]', '["REV Gen5 sport-utility"]', '["Scandi Blue","Black"]', None, 0, "Спорт-утилитарный"),
    ("skidoo-expedition-se-2026", "ski-doo", "snowmobile", "utility", 2026, "Expedition SE", "REV Gen5 SU", "850 E-TEC / 900 ACE Turbo R", "2/4-stroke", 849, 165, "CVT", "RWD", '["ACS rear shock","LinQ Premium 135L","10.25 дюйма display","removable passenger seat"]', '["REV Gen5 SU","uMotion"]', '["Terra Green","Black"]', None, 0, "Premium утилитарный"),
    # --- Sea-Doo ---
    ("seadoo-spark-60-2026", "sea-doo", "pwc", "rec-lite", 2026, "Spark for 2 – 60", "Spark", "Rotax 900 ACE", "4-stroke", 899, 60, "iTC", "Direct drive", '["iBR","LinQ","Slim seat"]', '[]', '["Sunrise Orange/Dragon Red"]', 6999, 0, "Лёгкий гидроцикл"),
    ("seadoo-spark-trixx-90-2026", "sea-doo", "pwc", "rec-lite", 2026, "Spark Trixx for 1 – 90", "Spark", "Rotax 900 ACE", "4-stroke", 899, 90, "iTC", "Direct drive", '["Extended VTS","footwell wedges","racing handlebar"]', '["NEW Gulfstream Blue/Orange Crush"]', '["Gulfstream Blue/Orange Crush","Dragon Red/White"]', 9499, 0, "Трюковый Spark"),
    ("seadoo-gti-se-170-2026", "sea-doo", "pwc", "recreation", 2026, "GTI SE 170", "GTI", "Rotax 1630 ACE", "4-stroke", 1630, 170, "iTC", "Direct drive", '["iDF","VTS","Boarding ladder","Ergolock seat","LinQ"]', '["NEW Eclipse Black/Laguna Green"]', '["Eclipse Black/Laguna Green","Teal Blue/Manta Green"]', 14699, 0, "Семейный гидроцикл"),
    ("seadoo-gtx-170-2026", "sea-doo", "pwc", "touring", 2026, "GTX 170", "ST3", "Rotax 1630 ACE", "4-stroke", 1630, 170, "iTC", "Direct drive", '["iDF","VTS","Ergolock seat","Direct-Access Front Storage"]', '[]', '["Blue Abyss/Gulfstream Blue"]', 20999, 0, "Туринг 170 HP"),
    ("seadoo-gtx-230-2026", "sea-doo", "pwc", "touring", 2026, "GTX 230", "ST3", "Rotax 1630 ACE SC", "sc", 1630, 230, "iTC", "Direct drive", '["iDF","VTS","audio","7.8 дюйма display","USB"]', '[]', '["Blue Abyss/Gulfstream Blue"]', 22999, 0, "Туринг Supercharged"),
    ("seadoo-gtx-limited-325-2026", "sea-doo", "pwc", "touring", 2026, "GTX Limited 325", "ST3", "Rotax 1630 ACE SC", "sc", 1630, 325, "iTC", "Direct drive", '["325 HP","premium audio","10.25 дюйма display","all features"]', '[]', '["White Pearl","Teal Metallic"]', 22549, 0, "Флагман туринга"),
    ("seadoo-rxp-x-325-2026", "sea-doo", "pwc", "performance", 2026, "RXP-X RS 325", "T3-R", "Rotax 1630 ACE SC", "sc", 1630, 325, "iTC", "Direct drive", '["325 HP","Launch Control","T3-R hull","Ergolock R seat","Extended VTS"]', '["NEW Gulfstream Blue Premium"]', '["Gulfstream Blue","Ice Metal/Manta Green"]', 20099, 0, "Race-ready 325 HP"),
    ("seadoo-gtr-230-2026", "sea-doo", "pwc", "performance", 2026, "GTR 230", "GTI", "Rotax 1630 ACE SC", "sc", 1630, 230, "iTC", "Direct drive", '["230 HP supercharged","VTS","Ergolock seat"]', '[]', '["Eclipse Black/Reef Blue"]', 14899, 0, "Доступная мощность"),
    ("seadoo-explorer-pro-170-2026", "sea-doo", "pwc", "touring", 2026, "Explorer Pro 170", "ST3", "Rotax 1630 ACE", "4-stroke", 1630, 170, "iTC", "Direct drive", '["Touring seat","rear deck ext","LinQ","audio","USB","steering damper"]', '[]', '["Iceland Grey"]', 20149, 0, "Экспедиционный"),
    ("seadoo-explorer-pro-230-2026", "sea-doo", "pwc", "touring", 2026, "Explorer Pro 230", "ST3", "Rotax 1630 ACE SC", "sc", 1630, 230, "iTC", "Direct drive", '["230 HP","touring seat","audio","LinQ","USB","steering damper"]', '[]', '["Iceland Grey"]', 20399, 0, "Экспедиционный SC"),
    ("seadoo-fishpro-sport-170-2026", "sea-doo", "pwc", "fishing", 2026, "FishPro Sport 170", "ST3", "Rotax 1630 ACE", "4-stroke", 1630, 170, "iTC", "Direct drive", '["5 rod holders","Garmin","fishing seat","cooler","iDF"]', '[]', '["White/Gulfstream Blue"]', None, 0, "Рыболовный гидроцикл"),
    ("seadoo-fishpro-trophy-170-2026", "sea-doo", "pwc", "fishing", 2026, "FishPro Trophy 170", "ST3", "Rotax 1630 ACE", "4-stroke", 1630, 170, "iTC", "Direct drive", '["Livewell","Garmin sonar","swivel seat","7 rod holders","rear deck ext"]', '[]', '["White/Gulfstream Blue"]', 22649, 0, "Профессиональный рыболовный"),
    ("seadoo-wake-pro-230-2026", "sea-doo", "pwc", "tow-sports", 2026, "Wake Pro 230", "ST3", "Rotax 1630 ACE SC", "sc", 1630, 230, "iTC", "Direct drive", '["230 HP","ski tow eye","audio","10.25 дюйма display","boarding ladder"]', '["10.25 дюйма touchscreen standard"]', '["Sand/Dazzling Blue"]', 19849, 0, "Буксировочный"),
    # --- Lynx ---
    ("lynx-rave-re-2026", "lynx", "snowmobile", "trail", 2026, "Rave RE", "Radien2", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["LFS-R front","PPS3 rear","KYB PRO 46 HLCR","10.25 дюйма display","LED headlights"]', '["LFS-R front","Electric Starter standard 850/600R"]', '["Black","Viper Red"]', None, 0, "Trail racer"),
    ("lynx-rave-gls-2026", "lynx", "snowmobile", "trail", 2026, "Rave GLS 40th", "Radien2", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["40th Anniversary","PPS3","KYB PRO 46 Kashima","Launch Control"]', '["40th Anniversary Edition"]', '["Viper Red/Black"]', None, 0, "40-летие Lynx"),
    ("lynx-adventure-limited-2026", "lynx", "snowmobile", "trail", 2026, "Adventure Limited", "Radien2", "900 ACE", "4-stroke", 899, 95, "CVT", "RWD", '["Premium LED","Heated Seat","iTC","Premium KYB","PPS3"]', '["3500mm track"]', '["Noble Bronze/Black"]', None, 0, "Premium туринг"),
    ("lynx-adventure-electric-2026", "lynx", "snowmobile", "trail", 2026, "Adventure Electric", "Radien2", "Rotax E-Power", "electric", None, None, "direct", "RWD", '["8.9 kWh battery","up to 50 km range","10.25 дюйма display","zero emissions"]', '["NEW — first electric Lynx"]', '["White"]', None, 1, "Первый электро-снегоход Lynx"),
    ("lynx-xt terrain-2026", "lynx", "snowmobile", "crossover", 2026, "Xterrain", "Radien2", "850 E-TEC / 900 ACE Turbo R", "2/4-stroke", 849, 165, "CVT", "RWD", '["crossover","KYB shocks","LED headlights"]', '[]', '["Black"]', None, 0, "Crossover"),
    ("lynx-xt terrain-limited-2026", "lynx", "snowmobile", "crossover", 2026, "Xterrain Limited", "Radien2", "900 ACE Turbo R", "4-stroke", 899, 180, "CVT", "RWD", '["Premium crossover","top engine","full features"]', '["NEW 2026"]', '["Black"]', None, 0, "NEW — Premium crossover"),
    ("lynx-brutal-re-2026", "lynx", "snowmobile", "mountain", 2026, "Brutal RE", "Radien2", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["Deep snow","Radien2 design","LED headlights"]', '[]', '["Black"]', None, 0, "Deep snow"),
    ("lynx-brutal-re-500mm-2026", "lynx", "snowmobile", "mountain", 2026, "Brutal RE 500mm", "Radien2", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["500mm wide track","Radien2 design","NEW"]', '["NEW 2026 — 500mm track"]', '["Black"]', None, 0, "NEW — 500mm гусеница"),
    ("lynx-shredder-re-2026", "lynx", "snowmobile", "mountain", 2026, "Shredder RE", "Radien2", "850 E-TEC Turbo R", "2-stroke", 849, 180, "CVT", "RWD", '["Ultimate deep snow","new agility","precision"]', '["NEW platform"]', '["Black"]', None, 0, "Ultimate mountain"),
    ("lynx-commander-re-2026", "lynx", "snowmobile", "utility", 2026, "Commander RE", "Radien2", "850 E-TEC / 900 ACE Turbo R", "2/4-stroke", 849, 165, "CVT", "RWD", '["Sporty utility","work and trail","Multi-LinQ"]', '["NEW — next-gen Commander"]', '["Black"]', None, 0, "NEW — спортивный utility"),
    ("lynx-69-ranger-pro-2026", "lynx", "snowmobile", "utility", 2026, "69 Ranger PRO", "Radien2", "600R E-TEC", "2-stroke", 599, 125, "CVT", "RWD", '["Full-size utility","LED headlights","Core package"]', '[]', '["Viper Red/Black"]', None, 0, "Рабочий снегоход"),
]

# ============================================================
# ACCESSORIES (из каталогов — ключевые)
# ============================================================
ACCESSORIES = [
    ("acc-001", "715008111", "can-am", "storage", "linq-box", "LinQ Tool Box (19L)", "Водонепроницаемый кофр 5 gal, запираемый, LinQ крепление.", '["G2","G2L","G2S"]', '[]', 299, 0, 0, '[]', '["кофр","хранение","linq","ящик","инструмент"]'),
    ("acc-002", "715008110", "can-am", "storage", "linq-bag", "LinQ Roll-Top Bag (40L)", "Водонепроницаемая сумка 10.6 gal, полностью герметичная.", '["G2","G2L","G2S"]', '[]', 249, 0, 0, '[]', '["сумка","хранение","linq","водонепроницаемая"]'),
    ("acc-003", "715003879", "can-am", "storage", "linq-cargo", "LinQ 12 Gal Cargo Box (45L)", "Герметичный кофр 12 gal, LinQ крепление.", '["G2","G2L","G2S"]', '[]', 399, 0, 0, '[]', '["кофр","багаж","linq"]'),
    ("acc-004", "715004923", "can-am", "storage", "linq-trunk", "LinQ 23 Gal Trunk Box (86L)", "Большой багажный кофр с подушкой для пассажира.", '["G2","G2L"]', '[]', 599, 0, 0, '[]', '["кофр","багаж","большой","пассажир"]'),
    ("acc-005", "715006829", "can-am", "storage", "linq-tools", "LinQ Tool Kit", "Полный набор инструментов + место для запасного ремня CVT.", '["G2","G2L","G2S"]', '[]', 349, 0, 0, '[]', '["инструмент","ремень","cvf","набор"]'),
    ("acc-010", "715010010", "can-am", "windshield", "flip-windshield", "Flip Windshield Hardcoated", "Стекло с 3 позициями, поликарбонат, антицарапочное покрытие.", '["G1","G1MAX","G2","G2MAX"]', '[]', 599, 1, 0, '[]', '["ветровое","стекло","ветер","защита"]'),
    ("acc-011", "715010876", "can-am", "windshield", "glass-windshield", "Glass Windshield", "Изогнутое ламинированное стекло, отличная видимость.", '["G1","G1MAX","G2","G2MAX"]', '[]', 799, 1, 0, '["715009441"]', '["ветровое","стекло","стеклянное"]'),
    ("acc-020", "715010568", "can-am", "doors", "front-doors", "Front Deluxe Full Doors (Clear)", "Премиум двери с замком, стеклоподъёмники, интегрированное хранение.", '["G2","G2MAX"]', '[]', 1299, 1, 1, '["715009756","715010489"]', '["двери","кабина","защита","полные"]'),
    ("acc-021", "715010572", "can-am", "doors", "half-doors", "Front Deluxe Half Doors", "Половинчатые двери с карманами, цветные панели отдельно.", '["G2","G2MAX"]', '[]', 699, 1, 0, '[]', '["двери","половинчатые","кабина"]'),
    ("acc-030", "715002430", "can-am", "roof", "sport-roof", "Sport Roof", "Прочный полипропиленовый козырёк от непогоды.", '["G1","G1MAX","G2","G2MAX"]', '[]', 499, 0, 0, '[]', '["крыша","козырёк","защита","дождь"]'),
    ("acc-031", "715010883", "can-am", "roof", "deluxe-roof", "Deluxe Sport Roof Kit", "Полный комплект крыши с уплотнителем и обшивкой.", '["G2","G2MAX"]', '[]', 899, 1, 0, '["715003126"]', '["крыша","люкс","кабина","тепло"]'),
    ("acc-040", "715010067", "can-am", "cab", "clear-rigid-cab", "Clear Rigid Cab (NEW)", "Жёсткая кабина с закалённым стеклом, раздвижные окна, замок.", '["G2","G2MAX"]', '[]', 2499, 1, 1, '["715002430","715010876","715009325"]', '["кабина","полная","стекло","тепло","новинка"]'),
    ("acc-050", "715008745", "can-am", "lighting", "smart-light-bar", "SMART 10-inch LED Light Bar", "Умная LED-балка, интегрируется с ACM.", '["Traxter G2","Defender"]', '[]', 399, 1, 0, '["715008755"]', '["свет","led","балка","фара","ночь"]'),
    ("acc-051", "715008111", "can-am", "lighting", "led-doublestack-15", "15-inch Double Stacked LED Light Bar 90W", "Мощная двойная LED-балка 5400 люмен.", '["Traxter","Defender"]', '[]', 499, 1, 0, '["715008755"]', '["свет","led","мощный","ночь"]'),
    ("acc-060", "708200963", "can-am", "mirrors", "side-mirror", "Side Mirrors (Left)", "Регулируемые зеркала на руль.", '["G2","G2L","G2S"]', '[]', 99, 0, 0, '[]', '["зеркало","видимость"]'),
    ("acc-070", "715006829", "can-am", "heating", "heated-grips", "Integrated Heated Grips & Thumb Combo", "Подогрев рукояток + курка, 5 режимов, IP67.", '["G2","G2L","G2S"]', '[]', 299, 0, 0, '[]', '["подогрев","руки","рукоятки","зима","комфорт"]'),
    ("acc-080", "715002418", "can-am", "bumper", "front-bumper", "Front Bumper", "Усиленный передний бампер, сталь 1.5 дюйма, крепление под LED.", '["G2","G2L"]', '[]', 349, 0, 0, '[]', '["бампер","защита","перед"]'),
    ("acc-081", "715009158", "can-am", "bumper", "rear-bumper-new", "Rear Bumper (NEW G2)", "Новый задний бампер с поручнем и защитой кофра.", '["G2","G2MAX"]', '[]', 349, 1, 0, '[]', '["бампер","зад","защита","новинка"]'),
    ("acc-090", "715003759", "can-am", "protection", "skid-plate-alum", "Aluminum Skid Plate Kit", "Алюминиевая защита днища 3/16 дюйма, полный комплект.", '["G3","G3L"]', '[]', 599, 0, 0, '[]', '["защита","днище","алюминий","скала"]'),
    ("acc-100", "715003046", "can-am", "protection", "skid-plate-hmwpe", "HMWPE Plastic Skid Plate", "Пластиковая защита 6мм, скользит по препятствиям.", '["G2","G2L"]', '[]', 399, 0, 0, '[]', '["защита","днище","пластик"]'),
    ("acc-110", "715003021", "can-am", "winch", "warn-3500", "Warn Winch 3500 lb Synthetic", "Лебёдка Warn 3500 lbs с синтетическим тросом.", '["Outlander","Defender"]', '[]', 849, 0, 0, '[]', '["лебёдка","вытаскивание","болото","грязь"]'),
    ("acc-120", "708200111", "can-am", "audio", "audio-bluetooth", "Bluetooth Handlebar Soundbar by MTX", "Bluetooth динамик 150W, IP66, управление с руля.", '["G2","G2L"]', '[]', 349, 0, 0, '[]', '["аудио","музыка","bluetooth","динамик"]'),
    ("acc-130", "860202449", "can-am", "storage", "linq-sport-bag", "LinQ 4.5 Gal Sport Bag (17L)", "Термоизолированная сумка-холодильник, LinQ.", '["G2","G2L","G2S"]', '[]', 199, 0, 0, '[]', '["сумка","холодильник","linq","еда"]'),
    # Ski-Doo accessories
    ("acc-200", "860202800", "ski-doo", "storage", "linq-touring-bag", "LinQ Touring Bag", "Большая сумка для туризма, LinQ крепление.", '["REV Gen5"]', '[]', 349, 0, 0, '[]', '["сумка","туризм","linq","багаж"]'),
    ("acc-201", "860201900", "ski-doo", "protection", "tunnel-protector", "Tunnel Protector Kit", "Защита тоннеля от цепей гусеницы.", '["REV Gen5"]', '[]', 129, 0, 0, '[]', '["защита","тоннель"]'),
    ("acc-202", "860201600", "ski-doo", "windshield", "tall-windshield", "Tall Windshield REV Gen5", "Высокое ветровое стекло для Trail, защита от ветра.", '["REV Gen5"]', '[]', 249, 0, 0, '[]', '["ветровое","стекло","ветер","защита"]'),
    ("acc-203", "860202500", "ski-doo", "heating", "heated-grips-sd", "Heated Grips Kit", "Подогрев рукояток, 4 режима.", '["REV Gen5"]', '[]', 249, 0, 0, '[]', '["подогрев","руки","зима"]'),
    ("acc-204", "860203000", "ski-doo", "lighting", "led-headlight-kit", "LED Auxiliary Light Kit", "Дополнительный LED свет, 1800 люмен.", '["REV Gen5"]', '[]', 299, 0, 0, '[]', '["свет","led","фара","ночь"]'),
    ("acc-205", "860202700", "ski-doo", "seats", "heated-seat", "Heated Trail Seat", "Подогрев сиденья с 3 режимами.", '["REV Gen5"]', '[]', 399, 0, 0, '[]', '["сиденье","подогрев","комфорт"]'),
    # Sea-Doo accessories
    ("acc-300", "779001476", "sea-doo", "storage", "linq-cooler-sd", "Sea-Doo LinQ Cooler (23L)", "Сумка-холодильник 23L для Sea-Doo.", '["ST3","GTI"]', '[]', 289, 0, 0, '[]', '["холодильник","еда","linq","сумка"]'),
    ("acc-301", "779001475", "sea-doo", "storage", "linq-bag-sd", "Sea-Doo LinQ Dry Bag (16L)", "Водонепроницаемая сумка LinQ для Sea-Doo.", '["ST3","GTI"]', '[]', 199, 0, 0, '[]', '["сумка","сухая","linq"]'),
    ("acc-302", "779000800", "sea-doo", "comfort", "boarding-ladder", "Retractable Boarding Ladder", "Выдвижная лестница для посадки с воды.", '["ST3","GTI"]', '[]', 249, 0, 0, '[]', '["лестница","посадка","вода"]'),
    ("acc-303", "779001300", "sea-doo", "audio", "audio-portable", "BRP Audio Portable", "Портативная аудиосистема Bluetooth для гидроцикла.", '["Spark","GTI","ST3"]', '[]', 399, 0, 0, '[]', '["аудио","музыка","bluetooth"]'),
    ("acc-304", "779001200", "sea-doo", "safety", "depth-finder", "Depth Finder", "Эхолот для измерения глубины.", '["FishPro"]', '[]', 299, 0, 0, '[]', '["эхолот","глубина","рыбалка"]'),
]

# ============================================================
# BUILD DATABASE
# ============================================================
def build_database():
    root = Path(__file__).parent
    db_path = root / DB_OUTPUT
    
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    
    conn = sqlite3.connect(str(db_path))
    c = conn.cursor()
    
    # Create tables
    c.executescript("""
        CREATE TABLE brp_models (
            id TEXT NOT NULL PRIMARY KEY,
            brand TEXT NOT NULL,
            category TEXT NOT NULL,
            subcategory TEXT NOT NULL,
            model_year INTEGER NOT NULL,
            modelName TEXT NOT NULL,
            platform TEXT,
            engineName TEXT,
            engineType TEXT,
            displacementCc REAL,
            horsepower INTEGER,
            transmission TEXT,
            driveType TEXT,
            keyFeatures TEXT,
            whatNew TEXT,
            colors TEXT,
            msrpUsd REAL,
            isElectric INTEGER NOT NULL,
            description TEXT
        );
        CREATE INDEX idx_models_brand ON brp_models(brand);
        CREATE INDEX idx_models_category ON brp_models(category);
        CREATE INDEX idx_models_subcategory ON brp_models(subcategory);
        
        CREATE TABLE brp_accessories (
            id TEXT NOT NULL PRIMARY KEY,
            sku TEXT NOT NULL,
            brand TEXT NOT NULL,
            category TEXT NOT NULL,
            subcategory TEXT,
            name TEXT NOT NULL,
            description TEXT NOT NULL,
            compatiblePlatforms TEXT,
            compatibleModels TEXT,
            msrpUsd REAL,
            isNew2026 INTEGER NOT NULL,
            requiresProfessionalInstall INTEGER NOT NULL,
            requiresParts TEXT,
            tags TEXT
        );
        CREATE INDEX idx_acc_brand ON brp_accessories(brand);
        CREATE INDEX idx_acc_category ON brp_accessories(category);
        CREATE INDEX index_brp_accessories_sku ON brp_accessories(sku);

        CREATE TABLE knowledge_cards (
            id TEXT NOT NULL PRIMARY KEY,
            equipmentType TEXT NOT NULL,
            brand TEXT NOT NULL,
            modelFamily TEXT,
            node TEXT NOT NULL,
            symptom TEXT NOT NULL,
            riskLevel TEXT NOT NULL,
            requiresEvacuation INTEGER NOT NULL,
            compatibleModels TEXT,
            causes TEXT NOT NULL,
            steps TEXT NOT NULL,
            canDo TEXT NOT NULL,
            mustNotDo TEXT NOT NULL,
            stopConditions TEXT NOT NULL,
            fullText TEXT NOT NULL
        );
        
        CREATE VIRTUAL TABLE knowledge_cards_fts USING fts4(
            fullText, symptom, id, causes,
            tokenize=unicode61,
            content=`knowledge_cards`
        );
        
        CREATE TABLE fault_codes (
            code TEXT NOT NULL PRIMARY KEY,
            brand TEXT NOT NULL,
            description TEXT NOT NULL,
            possibleCauses TEXT,
            solution TEXT
        );
        
        CREATE TABLE accessory_compatibility (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            accessoryId TEXT NOT NULL,
            modelId TEXT NOT NULL,
            fitmentNotes TEXT,
            requiresProfessionalInstall INTEGER NOT NULL,
            FOREIGN KEY(accessoryId) REFERENCES brp_accessories(id) ON UPDATE NO ACTION ON DELETE CASCADE ,
            FOREIGN KEY(modelId) REFERENCES brp_models(id) ON UPDATE NO ACTION ON DELETE CASCADE
        );
        CREATE INDEX index_accessory_compatibility_accessoryId ON accessory_compatibility(accessoryId);
        CREATE INDEX index_accessory_compatibility_modelId ON accessory_compatibility(modelId);

        -- ── Чат-сессии (схема v5/v6). Таблицы пустые: история чата —
        --    пользовательские данные, формируются в runtime. Нужны здесь,
        --    чтобы asset-БД сразу соответствовала @Database(version = 6)
        --    и Room открывала её без миграций при свежей установке. ──
        CREATE TABLE chat_sessions (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            vehicleId TEXT,
            vehicleName TEXT,
            mode TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE chat_messages (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            FOREIGN KEY (sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE
        );
        CREATE INDEX index_chat_messages_sessionId ON chat_messages(sessionId);

        -- Версия схемы Room. ДОЛЖНА совпадать с @Database(version) в BrpDatabase.kt.
        -- Без этого PRAGMA остаётся 0 → Room считает БД устаревшей и требует
        -- миграцию, которой нет → IllegalStateException при первом запуске.
        PRAGMA user_version = 6;

        -- room_master_table: identity-хэш схемы Room (из app/schemas/6.json).
        -- Room при открытии читает этот хэш; при совпадении он НЕ выполняет
        -- строгую валидацию и открывает pre-packaged asset напрямую.
        -- Без этой таблицы Room падает: расхождения (формат FTS, FK) блокируют
        -- открытие → «Ошибка БД» + пустые экраны, хотя ДАННЫЕ в asset есть.
        -- ⚠️ Если изменить entity-схему в BrpDatabase.kt — пересоберите проект и
        --    скопируйте НОВЫЙ identityHash из app/schemas/<v>.json сюда.
        CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
        INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '379b5e762bde447ee79bca801da4df98');
    """)
    
    # Insert models
    c.executemany("INSERT OR REPLACE INTO brp_models VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", MODELS)
    print(f"  ✅ {len(MODELS)} моделей BRP 2026")
    
    # Insert accessories
    c.executemany("INSERT OR REPLACE INTO brp_accessories(id,sku,brand,category,subcategory,name,description,compatiblePlatforms,compatibleModels,msrpUsd,isNew2026,requiresProfessionalInstall,requiresParts,tags) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", ACCESSORIES)
    print(f"  ✅ {len(ACCESSORIES)} аксессуаров")
    
    # Insert fault codes (Sea-Doo from research)
    fault_codes = [
        ("P0122", "sea-doo", "TPS: короткое замыкание на массу", '["Повреждён TPS","обрыв проводки"]', "Проверьте разъём TPS, проводку, замените TPS"),
        ("P0123", "sea-doo", "TPS: обрыв или КЗ на 12V", '["Повреждён TPS","corrosion"]', "Замените TPS, проверьте проводку"),
        ("P0513", "sea-doo", "Неверный DESS ключ", '["Чужой ключ","повреждён чип"]', "Используйте правильный ключ"),
        ("P0520", "sea-doo", "Неисправен датчик давления масла", '["Низкое давление масла","неисправен датчик"]', "Проверьте уровень масла, замените датчик"),
        ("P0562", "sea-doo", "Низкое напряжение батареи", '["Севший АКБ","неисправен статор"]', "Зарядите АКБ, проверьте зарядку"),
        ("P0536", "sea-doo", "Высокое напряжение батареи", '["Неисправен регулятор"]', "Замените регулятор напряжения"),
        ("P0600", "sea-doo", "CAN ошибка связи", '["Повреждён жгут","сгорел предохранитель MPEM"]', "Проверьте предохранитель 2A MPEM, проводку CAN"),
        ("P1148", "sea-doo", "Форсунка: safety fuel cutoff", '["Обрыв жгута форсунок","неисправна форсунка"]', "Проверьте жгут форсунок, сопротивление"),
        ("P0122", "can-am", "TPS: короткое замыкание на массу", '["Повреждён TPS","влага в разъёме"]', "Проверьте разъём, замените TPS"),
        ("P0123", "can-am", "TPS: обрыв или КЗ на 12V", '["Повреждён TPS"]', "Замените TPS"),
        ("P0335", "can-am", "Неисправен датчик коленвала (CPS)", '["Повреждён датчик","проводка"]', "Замените CPS"),
        ("P0562", "can-am", "Низкое напряжение бортовой сети (<11.5V)", '["Слаб АКБ","окисление контактов","регулятор напряжения"]', "Проверьте клеммы АКБ, заряд статора"),
        ("P0500", "can-am", "Датчик скорости колеса (VSS Fault)", '["Грязь на гребенке ABS","обрыв датчика скорости"]', "Промойте гребенки датчиков колес, проверьте зазоры"),
        ("P0700", "can-am", "Неисправность контроллера коробки (DCT/SE6)", '["Низкое давление масла коробки","перегрев сцепления"]', "Остановитесь, дайте остыть на холостых в N/P"),
        ("P0264", "ski-doo", "Неисправность жгута форсунок", '["Обрыв внутри изоляции (ЧАСТО!)"]', "Проверьте жгут в 2-3 дюймах от разъёма, прозвоните"),
        ("P0141", "ski-doo", "Ошибка привода клапанов RAVE", '["Нагар на клапанах RAVE","заклинивание шторки"]', "Прочистите клапаны RAVE от нагара и масла"),
        ("P1488", "ski-doo", "Перегрев выхлопа (EGT Sensor High)", '["Бедная смесь","подсос воздуха","плохой бензин"]', "Остановитесь, проверьте патрубки впуска на подсос"),
        ("P0264", "lynx", "Неисправность форсунки E-TEC", '["Обрыв проводки форсунки в перегибе жгута"]', "Проверьте жгут форсунок под коробом впуска"),
        ("P0141", "lynx", "Ошибка положения клапана RAVE", '["Налипание масляного шлака на клапаны RAVE"]', "Снимите крышку RAVE клапана, очистите шторку карбклинером"),
        ("P1488", "lynx", "Превышение температуры выхлопных газов (EGT)", '["Недостаточное охлаждение в снегу","бедная смесь"]', "Выедьте в пухляк, опустите скребки Ice Scratchers"),
    ]
    c.executemany("INSERT OR REPLACE INTO fault_codes VALUES (?,?,?,?,?)", fault_codes)
    print(f"  ✅ {len(fault_codes)} кодов ошибок")
    
    # Parse knowledge cards from markdown files
    knowledge_dir = root / KNOWLEDGE_DIR
    card_count = 0
    for md_file in knowledge_dir.rglob("*.md"):
        card = parse_knowledge_card(md_file)
        if card:
            c.execute("INSERT OR REPLACE INTO knowledge_cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", card)
            card_count += 1
    print(f"  ✅ {card_count} карточек знаний")
    
    conn.commit()
    
    # Stats
    total = c.execute("SELECT COUNT(*) FROM brp_models").fetchone()[0]
    acc_total = c.execute("SELECT COUNT(*) FROM brp_accessories").fetchone()[0]
    kb_total = c.execute("SELECT COUNT(*) FROM knowledge_cards").fetchone()[0]
    fc_total = c.execute("SELECT COUNT(*) FROM fault_codes").fetchone()[0]
    
    size_kb = db_path.stat().st_size / 1024
    print(f"\n📊 ИТОГО: {total} моделей | {acc_total} аксессуаров | {kb_total} карточек | {fc_total} кодов ошибок")
    print(f"📁 Файл: {db_path} ({size_kb:.0f} KB)")
    
    conn.close()

def parse_knowledge_card(filepath: Path) -> tuple:
    text = filepath.read_text(encoding="utf-8")
    
    # Parse YAML-like frontmatter between ---
    meta = {}
    body = text
    fm_match = re.match(r'^---\s*\n(.*?)\n---\s*\n(.*)', text, re.DOTALL)
    if fm_match:
        for line in fm_match.group(1).split("\n"):
            if ":" in line:
                key, val = line.split(":", 1)
                meta[key.strip()] = val.strip().strip('"').strip("'")
        body = fm_match.group(2)
    
    card_id = meta.get("id", filepath.stem)
    brand = meta.get("brand", "brp")
    
    # Extract sections with synonyms for headings
    causes = (extract_json_list(body, "Вероятные причины") or 
              extract_json_list(body, "Причины") or 
              extract_json_list(body, "Причины возникновения") or 
              extract_json_list(body, "Симптомы и причины"))
              
    can_do = (extract_json_list(body, "Что МОЖНО") or 
              extract_json_list(body, "Полевой ремонт") or 
              extract_json_list(body, "Действия в полевых условиях") or 
              extract_json_list(body, "Замена в полевых условиях") or 
              extract_json_list(body, "Экстренные действия") or 
              extract_json_list(body, "Что делать") or 
              extract_json_list(body, "Решение в полевых условиях"))
              
    must_not = (extract_json_list(body, "Чего делать НЕЛЬЗЯ") or 
                extract_json_list(body, "Чего делать нельзя") or 
                extract_json_list(body, "НЕЛЬЗЯ") or 
                extract_json_list(body, "Запрещено") or 
                extract_json_list(body, "Опасности"))
                
    stop = (extract_json_list(body, "Когда прекратить") or 
            extract_json_list(body, "Когда к дилеру") or 
            extract_json_list(body, "Обращение к дилеру") or 
            extract_json_list(body, "Когда обращаться в профессиональный сервис") or 
            extract_json_list(body, "Профессиональный ремонт"))
    
    return (
        card_id,
        meta.get("equipment_type", ""),
        brand,
        meta.get("model_family"),
        meta.get("node", ""),
        meta.get("symptom", ""),
        meta.get("risk_level", "medium"),
        int(meta.get("requires_evacuation", "false").lower() == "true"),
        meta.get("compatible_models", "[]"),
        json.dumps(causes, ensure_ascii=False),
        "[]",  # steps - complex, stored in fullText
        json.dumps(can_do, ensure_ascii=False),
        json.dumps(must_not, ensure_ascii=False),
        json.dumps(stop, ensure_ascii=False),
        body.strip()
    )

def extract_json_list(text: str, heading: str) -> list:
    items = []
    in_section = False
    for line in text.split("\n"):
        if heading.lower() in line.lower():
            in_section = True
            continue
        if in_section:
            if line.startswith("##") or (line.strip() and not line.strip().startswith(("-", "*", "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9."))):
                if items:
                    break
            stripped = line.strip().lstrip("-* 0123456789. ")
            if stripped and not stripped.startswith("#"):
                items.append(stripped)
    return items

if __name__ == "__main__":
    print("🔧 Сборка brp_assistant.db...")
    build_database()
    print("✅ Готово!")
