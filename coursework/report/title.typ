#let title_page(
  worktype: [По курсовой работе],
  theme: [CRM для Карсти Крабс],
  students: [Ильин Н. С., Алхимовици А., гр. Р3310],
  teacher: [Воронина Д. С.],
  date: "2025",
) = [
  // Верхняя часть - центр
  #align(center)[
    #text(weight: "bold")[НАЦИОНАЛЬНЫЙ ИССЛЕДОВАТЕЛЬСКИЙ УНИВЕРСИТЕТ ИТМО]
    #v(1em)
  
    #text[Факультет программной инженерии и компьютерной техники]
    #text[Направление подготовки 09.03.04 Программная инженерия]
    #text[Дисциплина «Информационные системы»]
    #v(2.5em)
  
    #text(size: 16pt, weight: "bold")[ОТЧЕТ]
    #v(0.5em)
    #text(worktype)
    #v(0.5em)
    #text[«#theme»]
  ]

  #v(3cm)

  #align(right + horizon)[
    #text(weight: "bold")[Студенты:] #linebreak() 
    #text(students)
    #v(1.2em)

    #text(weight: "bold")[Преподаватель:] #linebreak() 
    #text(teacher)
  ]

  #v(3cm)

  // Нижняя часть
  #align(center + bottom)[
    #text[Санкт-Петербург] 
    #linebreak()
    #text(date + " г.")
  ]
]

