import { useRef, useState } from 'react'
import { validateFiles } from './api'
import './App.css'

const STATUS = {
  OK: { label: 'Ошибок нет', cls: 'ok' },
  ERRORS: { label: 'Есть ошибки', cls: 'err' },
  UNRECOGNIZED: { label: 'Не распознан', cls: 'warn' },
  FAILED: { label: 'Не прочитан', cls: 'fail' },
}

const fmt = (n) => Number(n).toLocaleString('ru-RU')

function Summary({ summary }) {
  const items = [
    ['Файлов', summary.tables, ''],
    ['Без ошибок', summary.ok, 'ok'],
    ['С ошибками', summary.withErrors, 'err'],
    ['Не распознано', summary.unrecognized, 'warn'],
    ['Не прочитано', summary.failed, 'fail'],
    ['Всего ошибок', summary.totalIssues, 'err'],
  ]
  return (
    <div className="summary">
      {items.map(([label, value, cls]) => (
        <div key={label} className={`stat ${value > 0 ? cls : ''}`}>
          <b>{fmt(value)}</b>
          <span>{label}</span>
        </div>
      ))}
    </div>
  )
}

function ColumnReport({ report }) {
  const hidden = report.errorCount - report.issues.length
  return (
    <div className="column">
      <div className="column-head">
        <code>{report.column}</code>
        <span className="muted">
          ошибок: {fmt(report.errorCount)} · проверено строк: {fmt(report.checked)}
        </span>
      </div>
      <p className="rule">Правило: {report.rule}</p>
      <div className="scroll">
        <table>
          <thead>
            <tr><th>Строка</th><th>Значение</th><th>Причина</th></tr>
          </thead>
          <tbody>
            {report.issues.map((issue, i) => (
              <tr key={i}>
                <td>{issue.row > 0 ? issue.row : '—'}</td>
                <td className="mono">{issue.value || <span className="muted">пусто</span>}</td>
                <td>{issue.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {hidden > 0 && <p className="muted small">Показаны первые {report.issues.length}, ещё {fmt(hidden)} не показаны.</p>}
    </div>
  )
}

function FileCard({ file }) {
  const status = STATUS[file.status] ?? STATUS.FAILED
  return (
    <details className="card" open={file.status === 'ERRORS'}>
      <summary>
        <span className={`badge ${status.cls}`}>{status.label}</span>
        <span className="file-name">{file.fileName}</span>
        {file.recognized && <span className="muted">{file.table}</span>}
      </summary>
      <div className="card-body">
        <p>{file.message}</p>
        {file.recognized && (
          <p className="muted small">
            Строк: {fmt(file.rows)} · колонок в схеме: {fmt(file.columns)} · ошибок: {fmt(file.errorCount)} в колонках: {fmt(file.columnsWithErrors)}
          </p>
        )}
        {file.warnings.length > 0 && (
          <ul className="warnings">
            {file.warnings.map((w, i) => <li key={i}>{w}</li>)}
          </ul>
        )}
        {file.columnReports.map((r) => <ColumnReport key={r.column} report={r} />)}
      </div>
    </details>
  )
}

export default function App() {
  const inputRef = useRef(null)
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  async function run(fileList) {
    const files = [...fileList]
    if (!files.length) return
    setLoading(true)
    setError('')
    try {
      setResult(await validateFiles(files))
    } catch (e) {
      setResult(null)
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <header>
        <h1>Проверка XLSX-выгрузок</h1>
        <p className="muted">Перетащите таблицы — таблица определится по шапке, а ошибки появятся ниже.</p>
      </header>

      <div
        className={`drop ${dragging ? 'drag' : ''} ${loading ? 'busy' : ''}`}
        onClick={() => !loading && inputRef.current.click()}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); if (!loading) run(e.dataTransfer.files) }}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".xlsx,.xls"
          hidden
          onChange={(e) => { run(e.target.files); e.target.value = '' }}
        />
        <strong>{loading ? 'Проверяем…' : 'Перетащите .xlsx сюда'}</strong>
        <span className="muted">{loading ? 'Это может занять время для больших файлов' : 'или нажмите, чтобы выбрать файлы (до 50 МБ каждый)'}</span>
      </div>

      {error && <div className="alert">{error}</div>}

      {result && (
        <section className="results">
          <Summary summary={result.summary} />
          {result.files.map((f, i) => <FileCard key={`${f.fileName}-${i}`} file={f} />)}
        </section>
      )}
    </main>
  )
}