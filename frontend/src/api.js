const HTTP_ERRORS = {
  400: 'Сервер не получил ни одного непустого файла.',
  413: 'Файл больше 50 МБ или суммарный размер больше 500 МБ.',
  415: 'Сервер ожидает multipart/form-data.',
}

// POST /api/validate — один или несколько XLSX в части "files"
export async function validateFiles(files) {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))

  let response
  try {
    response = await fetch('/api/validate', { method: 'POST', body: form })
  } catch {
    throw new Error(
      'Не удалось связаться с сервером. Проверьте, что backend запущен на http://localhost:8080.',
    )
  }

  if (!response.ok) {
    throw new Error(HTTP_ERRORS[response.status] ?? `Ошибка сервера: HTTP ${response.status}`)
  }
  return response.json()
}