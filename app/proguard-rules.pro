# Room, Compose, Navigation и Lifecycle поставляют собственные consumer-правила R8.
# Статусы в БД хранятся через явное поле ChannelStatus.dbValue, поэтому
# переименование enum-констант при обфускации не влияет на данные.

# Оставляем имена классов в стек-трейсах читаемыми
-keepattributes SourceFile,LineNumberTable
