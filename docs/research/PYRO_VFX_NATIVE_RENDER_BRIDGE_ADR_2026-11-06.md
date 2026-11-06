> Historical proof-of-concept research. For the implemented production architecture and updated validation, see [Native Render Bridge](../NATIVE_RENDER_BRIDGE.md). Screenshots referenced below may be in the external preserved PoC archive.

# ADR — Native Render Bridge для Pyro VFX

**Дата:** 2026-11-06
**Статус:** архитектурный выбор — ПРИНЯТ; **поэтапная feature-gated миграция разрешена**, полная безусловная замена — **НЕ ПОДТВЕРЖДЕНА**.
**Текущий вывод (последняя проверка):** [NATIVE_BRIDGE_MIGRATION_READINESS_2026-11-06.md](NATIVE_BRIDGE_MIGRATION_READINESS_2026-11-06.md): компактный 4-vertex GPU Bridge, 100k через bounded chunks, ресурсный reload, консервативный RenderType fallback, настоящий vanilla CLOUD тоже невидим под водой с Complementary. Остаточные ограничения прозрачного порядка и material feature parity требуют поэтапного переноса.
**Позднейшее исправление PoC:** [BRIDGE_FIX_VALIDATION_2026-11-06.md](BRIDGE_FIX_VALIDATION_2026-11-06.md). Исправлены треугольники, восстановление OpenGL и повторное использование birth VBO. Переход на NeoForge `AFTER_BLOCK_ENTITIES` устранил исчезновение **аддитивных** частиц за стеклом и в воде в проверенных vanilla/Iris сценах без отключения depth. **GPU alpha внутри воды с Complementary остаётся невидимой**, включать Analytic Alpha в production нельзя. Записи ниже об исходных провалах относятся к предыдущей версии прототипа.
**Цель:** единая система VFX с GPU-вычислениями и нативной композицией Minecraft/Iris, без Java-объекта на каждую GPU-частицу.

## Решение

**Pyro вычисляет состояние частиц и готовит геометрию. Minecraft/NeoForge отвечает за draw submission, материалы, целевой render pass и интеграцию с Iris/shaderpacks.**

Мы создаём **Native GPU Geometry Bridge** между существующими SoA / GPU Analytic / Instancing системами и стандартными шейдерами/вершинными форматами Minecraft. Все renderer backends собирают одни и те же декларативные материальные партии: `sprite_additive`, `sprite_alpha`, `sprite_cutout`, `baked_block`, `baked_item`, `custom_model` и т. д. Материалы группируются по состоянию/текстуре/порядку прозрачности.

При GPU Analytic стандартный путь:

```text
VFX JSON/compiled program
  -> existing scheduler + particle pools
  -> birth-state VBO (once per cohort, unchanged)
  -> GPU transform-feedback prepass (GL 3.3)
      transforms motion, size, color, camera-facing, UV into GPU vertex output
  -> native Minecraft vertex attributes (PARTICLE/BLOCK/NEW_ENTITY by material)
  -> Minecraft ShaderInstance / RenderType compatible draw
  -> native Minecraft/Iris framebuffer targets and final composition
```

**Нет CPU readback, нет Java Particle per instance, нет покадрового CPU upload вершин GPU-частиц**; CPU обновляет только uniform/batch descriptors, вызывает GPU prepass и draw. Данные на GPU **перезаписываются GPU** каждый кадр, что является реальной стоимостью по bandwidth/VRAM; нельзя говорить «вообще никаких покадровых передач».

**Первый предпочитаемый hook:** штатный NeoForge `RenderLevelStageEvent`, для подходящих прозрачных спрайтов `AFTER_PARTICLES`, при других материалах ранняя точка с правильным material render state. **Внутренний Mixin Iris и reflection-смена фаз не требуются для работающего прототипа открытого неба.** Не превращать Stage в глобальное правило для абсолютно всех материалов.

## Почему не raw GLSL как финальный рендер

В Iris shaderpack может перехватывать/заменять собственные шейдеры Minecraft и композитить отдельные G-buffer/deferred targets. Случайный `glUseProgram(customPyroFragmentShader)` в активный FBO не означает, что цвет окажется в финальном кадре. Это подтверждено direct FBO readbacks: тестовый magenta rectangle появился после raw draw и исчез после Iris composite; Pyro Analytic raw draw был невидим сразу после отрисовки, даже без depth test. Прямой Minecraft `ParticleRenderType` draw попал в финальный кадр.

Официальная документация Iris прямо предупреждает о принципиальной несовместимости произвольных core shaders с shaderpacks и рекомендует fallback без собственного fragment/vertex material shader:
https://github.com/IrisShaders/Iris/blob/26.1/docs/development/compatibility/core-shaders.md

**Разделение ответственности:** пользовательский GLSL допустим в **GPU geometry generation** (без записи пикселей в собственный шейдерпаковый G-buffer), но вывод на экран — через Minecraft-managed shader/material.

## Проверенный PoC

Изолированное рабочее дерево (не production):
`C:\Users\valle\AppData\Roaming\LocalCoderStudio\profiles\scratch\pyro-vfx\misc\stage-target-poc` (base `7ddae4d`).

**Класс:** `src/main/java/net/kofllee/pyrovfx/client/render/VfxNativeParticleTfProbe.java`.

- Реальный `VfxAnalyticParticlePool.gpuBuffer()` с 1,000 birth records: первый раз **24,000 B** upload (последующие кадры **0 B**).
- GL3.3 transform feedback превращает их в **6,000 вершин** в **GPU-only** выходной VBO.
- Исходный экспериментальный формат 44 B/вершина: 3 float position, 2 float UV, 4 float color, 2 int light. При выделении под 1024 частицы выходной VBO **270,336 B**.
- Финальный draw использует `GameRenderer.getParticleShader()`, `ShaderInstance.setDefaultUniforms()/apply()/clear()` и native attributes; выставляет корректную camera-relative matrix, Minecraft lightmap и texture binding. При работе с активным Iris использовался **не** его внутренний Mixin, а обычный **NeoForge AFTER_PARTICLES**.
- Ошибок OpenGL не зафиксировано; `glDrawArrays` выполнялся многократно без CPU обновления birth VBO.
- **Видимый результат в открытом пространстве:** ванильные **Fast / Fancy / Fabulous** и Iris 1.8.12 + Sodium 0.6.13 + активный Complementary Reimagined r5.9.3. Скриншоты: `docs/research/native-gpu-bridge-2026-11-06/`.
- Это **только** тестовое аддитивное 1k sprite cohort, с упрощённой моделью появления, фиксированным fullbright в TF, без полного GPU appearance, корректного world/lightmap sample, локальных трансформов, UV-кадров, model faces, разных альфа-проходов и контролируемого релиза VBO. Нельзя считать PoC production-ready.

**Исторический недостаток начального PoC (до исправления раннего stage):** на активном Complementary за сплошной стеклянной стеной и внутри заполненного водой резервуара эти GPU-to-GPU частицы **не видны**, несмотря на draw. **Позже это устранено для аддитивного материала, но не для alpha**, см. новый отчёт. Ванильный Fast/Fancy также имеет пропадание частиц в воде: предыдущие эксперименты показали, что отключение depth test делает частицы видимыми, но создаёт неверное просвечивание через твёрдые блоки. Старый early/late renderer тоже проваливает полностью заполненный водный объём. **Ни Minecraft ShaderInstance, ни GPU prepass сами по себе не исправляют inter-layer transparency.**

Архитектура выбрана, потому что GPU-source -> native draw технически проходит базовые рендереры. **Разрешения мигрировать весь движок пока нет**: остаётся отдельная сложная задача прозрачности, совместимости shaderpacks, материалов и качества эффекта.

## Контракт производственного Render Bridge

1. **GeometrySource** абстрагирует чтение из CPU dense pools / GPU Analytic birth VBO / GPU instancing. Для быстрых GPU-случаев вывод — **GPU VBO with native vertex attributes**.
2. **Material descriptor** определяет текстуру/атлас, режим `solid/cutout/alpha/additive`, writesDepth/depthTest, lighting, culling, sorting и стандартный Minecraft `ShaderInstance` / `RenderType`. Не допускать глобального состояния «все частицы = один shader».
3. **RenderPassRouter** выбирает точку и output target по **материалу и реальным возможностям графического конвейера**, не по имени/ID блока, не по raycast до центральной частицы. В Fabulous сохраняется native `particlesTarget` и Minecraft composite. С shaderpacks используем их нативный ShaderInstance/RenderType output.
4. **GPU prepass** повторяет реальные расчёты текущего Analytic: spawn/age/velocity/acceleration/drag, expressions, local-world attachment, facing, scale/rotation, curves, per-particle lifetime, UV animation и texture. Согласовать их с CPU golden fixtures.
5. **Light/fog/material contract:** стандартный lightmap/sampler, vertex color/alpha/UV, shaderpack G-buffer semantics, fog, camera matrices, resource reload; для моделей собственные форматы `BLOCK`/`NEW_ENTITY`, а не `PARTICLE`. Не пытаться заменить свет/тени raw собственным fragment shader.
6. **CPU fallback** — dense CPU simulation + батчевый native RenderType/VBO, **не** по одному Minecraft Particle/Java object. На GL <3.3, ошибка TF, сложные block/item/entity models или unsupported effects — fallback с диагностикой. При активном Iris GPU compute/analytic **не отключаются автоматически**: совместимый GPU-prepass сохраняет ускорение.
7. **Instancing:** для простых sprites/models исследовать GPU-to-GPU flattening в нативный VBO; complex models/animations/foreign renderer -> Minecraft native CPU fallback до подтверждения совместимости. Нельзя обобщить одну модельную технику на arbitrary baked/entity geometry без проверки.
8. **Состояние GL:** строго restore VAO, GL programs, active texture, lightmap, framebuffer, blend, depth, viewport, rasterizer-discard, TF bindings; invalidate resource generation on shaderpack change / resource reload / world close. Scoped GPU fences/queries, без блокирующего readback в кадре.

## Прозрачность: отдельный обязательный correctness gate

**Никакой универсальный "идеальный alpha order" над водой, стеклом и поверхностями других модов не гарантируется** при произвольных шейдерпаках, если их графические конвейеры не предоставляют совместимый механизм композиции. Приоритет — уровень корректности **не ниже ванильных частиц**, отсутствие регрессий в значимых сценах; особые эффекты могут требовать documented quality limitation.

1. Opaque depth должен отсекать частицы за камнем/землёй. Нельзя выключать `GL_DEPTH_TEST` глобально.
2. Раздельные ранний/поздний native passes по **материалу**, с корректным compositing target. Для воды проверить opaque-depth / translucent-depth до и после воды и при необходимости использовать shaderpack-aware composition, а не block-ID heuristics.
3. Alpha smoke: разбивка на пригодные batches, depth-aware sorting (GPU где оправдано); при невозможности попиксельного смешения — зафиксировать ограничение; не создавать случайные кадры поверх воды или за стеклом.
4. Набор тестов: front/behind/intersecting glass, air/water surface, fully-submerged smoke, camera underwater, multiple overlapping emitters, opaque occlusion, depth-writing translucent mods, additive/alpha/cutout materials, shaderpack on/off, Fabulous.
5. **Не удалять старую production интеграцию до прохождения gate.**

## Performance / memory

Текущий PoC генерирует 6 вершин по 44 B = **264 B на частицу** output, то есть примерно **264 MB для 1M** без учёта birth VBO, двойной буферизации и staging. Это **не целевой оптимизированный формат**. В production перейти на индексируемые 4 вершины/quad и более компактные атрибуты (где возможно), группировку материалов, output VBO capacity budgets и adaptive LOD. Измерять GPU timer/VRAM/fill-rate, а не только CPU submission. Transform feedback может оказаться дороже прямого analytic draw — измерить на 1k/10k/100k. Для 1M не обещать скорость без реального shaderpack-бенчмарка.

Разрешённый optional fast path: существующий raw GPU analytic renderer **при выключенном shaderpack и только если native parity совпадает**. Он может экономить output VBO bandwidth. Но не делать второй независимый композитор со своими визуальными правилами и не полагаться на shaderpack-name хардкоды.

## Порядок внедрения

**R1 — Native sprite bridge:** продакшен-версия TF/GeometrySource, stable GPU lifecycle и material batches, actual GPU motion/appearance parity, корректный fullbright/worldlight/alpha, FPS и timing counters. Feature flag и rollback.

**R2 — Transparency integration:** материал-ориентированные native passes (opaque/cutout/alpha/additive), world depth, native Fabulous particle target и совместимая Iris-композиция; регрессионные сценарии water/glass, не block raycast.

**R3 — CPU/Instancing/Models:** CPU dense pool в тот же RenderBatch, простые модели через нативные vertex formats, сложные модели через штатный RenderType. Проверить shaderpack normals/lighting.

**R4 — Migration gate:** Fast/Fancy/Fabulous, Iris + Complementary и как минимум ещё один независимый shaderpack, shaderpack reload/toggle, stress + switching worlds, alpha smoke, attached entities, multiple overlapping VFX, GL audit. Сравнение CPU/GPU color/position and screenshots на фиксированных seed.

**R5 — Cutover:** только после pass всех gates убрать `early/late` raycast, свернуть raw custom fragment shader как основной renderer, оставить быстрые опции только под контролем capability matrix и verified parity.

**Финальный контракт: Pyro отвечает за симуляцию и геометрию, Minecraft — за материал, вывод и место изображения в кадре.** Архитектура подтверждена в базовом сценарии, но release-readiness сознательно не заявлена.
