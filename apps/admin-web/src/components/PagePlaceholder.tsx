import type { ReactNode } from "react";

interface PagePlaceholderProps {
  title: string;
  description: string;
  children?: ReactNode;
}

/** 页面骨架统一样式，只承载标题与待接入说明，不含业务逻辑。 */
export function PagePlaceholder({
  title,
  description,
  children,
}: PagePlaceholderProps) {
  return (
    <section className="page">
      <header className="page-header">
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </header>
      {children ? <div className="page-body">{children}</div> : null}
    </section>
  );
}
