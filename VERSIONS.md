# Lunar Launcher — полный каталог версий Minecraft Java

Каталог версий Lunar получает данные из официального Mojang manifest и кэширует их локально.

## Источник

`MinecraftVersionRepository.kt` читает официальный публичный version manifest Minecraft Java:

`https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`

Manifest содержит идентификатор версии, тип, дату и URL JSON-метаданных конкретной версии.

## Что поддерживает каталог

- Release
- Snapshot / pre-release / release candidate (все записи с типом `snapshot`)
- Old Beta (`old_beta`)
- Old Alpha (`old_alpha`)
- Поиск по номеру/названию версии
- Фильтр по типу
- Последний release и snapshot
- Локальный кэш, чтобы список открывался без интернета после первой загрузки
- Кнопка принудительного обновления

## Важно

Это каталог версий, а не встроенные копии Minecraft. Для запуска конкретной версии Lunar должен отдельно получить разрешённые пользователю игровые файлы и зависимости из метаданных выбранной версии.
