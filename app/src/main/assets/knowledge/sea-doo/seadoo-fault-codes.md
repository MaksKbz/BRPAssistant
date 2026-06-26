---
id: "seadoo-fault-codes"
equipment_type: "pwc"
brand: "sea-doo"
model_family: "all"
node: "ecu"
symptom: "Ошибки на дисплее, Check Engine, DESS не срабатывает"
risk_level: "variable"
requires_evacuation: false
compatible_models: ["sea-doo-spark","sea-doo-gti","sea-doo-gtx","sea-doo-rxp-x","sea-doo-rxt-x"]
---
# Sea-Doo — Коды ошибок iBR/ECM

## Как читать коды
Sea-Doo 2012+ использует систему iTC (intelligent Throttle Control). Коды отображаются на цветном дисплее или считываются через BRP BUDS2. На Spark и GTI коды показываются миганием индикатора.

## Частые коды
| Код | Система | Описание | Действие |
|-----|---------|----------|----------|
| P0117 | ECT | Датчик температуры ОЖ — низкое | Проверить разъём |
| P0335 | Crank | Нет сигнала датчика коленвала | Зазор, загрязнение |
| P1562 | Fuel | Давление топлива вне нормы | Насос, фильтр |
| P2135 | TPS | Несоответствие TPS1/TPS2 | Калибровка BUDS |
| C0010 | iBR | Неисправность реверса iBR | Проверить трос |
| C0020 | iBR | Позиция iBR не определена | Заклинило bucket |
| U0001 | CAN | Ошибка CAN-шины | Питание/земля ECM |
| P0480 | Cooling | Вентилятор охлаждения (GTX/RXP) | Реле, двигатель вент. |

## Важно
Коды P1XXX специфичны для BRP — не путать с обычным OBD2. После устранения причины сброс кодов только через BUDS2 или отключением АКБ на 10 мин.
