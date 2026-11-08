import type { Problem } from '../api';

export function ProblemMessage({ problem }: { problem: Problem }) {
  const fieldErrors = problem.errors ? Object.entries(problem.errors) : [];
  return (
    <div className="notice notice--error" role="alert">
      <strong>
        {problem.status} · {problem.title}
      </strong>
      {problem.detail && <p>{problem.detail}</p>}
      {fieldErrors.length > 0 && (
        <ul>
          {fieldErrors.map(([field, message]) => (
            <li key={field}>
              <code>{field}</code> {message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
