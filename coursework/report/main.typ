// Главный документ Typst: титульный лист + остальной отчет

// Глобальный стиль/макет документа
#set page(paper: "a4", margin: (x: 2.5cm, y: 2cm))
#set text(lang: "ru", region: "RU", font: ("Times New Roman"), size: 12pt)
#set par(justify: true)

// 1) Титульный лист
#import "title.typ": title_page
#title_page()

#pagebreak()

// Автоматическое оглавление (убираем дублирующийся заголовок)
#outline(title: [Содержание])

#pagebreak()

// 2) Содержимое отчета, сконвертированное из DOCX pandoc'ом
#include "content.typ"

// 3) Конец документа
