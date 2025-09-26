import { useEffect } from 'react';
import { useOutletContext } from 'react-router-dom';

export function usePageMeta(meta) {
  const context = useOutletContext();
  const { setPageMeta } = context || {};

  useEffect(() => {
    if (!setPageMeta) {
      return undefined;
    }

    setPageMeta(meta);
    return () => {
      setPageMeta((prev) => ({ ...prev, actions: null }));
    };
  }, [meta, setPageMeta]);

  return context;
}
