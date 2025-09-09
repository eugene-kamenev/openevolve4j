import React from 'react';
import { Plus, Minus } from 'lucide-react';

const ArrayInput = ({ 
  items = [], 
  onAdd, 
  onRemove, 
  onChange, 
  placeholder = 'Enter value',
  addLabel = 'Add Item',
  renderItem
}) => {
  return (
    <div className="array-input">
      {items.map((item, index) => (
        <div key={index} className="array-item">
          {renderItem ? (
            renderItem(item, index, onChange, onRemove)
          ) : (
            <>
              <input
                type="text"
                value={item}
                onChange={(e) => onChange(index, e.target.value)}
                placeholder={placeholder}
              />
              <button
                type="button"
                onClick={() => onRemove(index)}
                className="btn-remove"
              >
                <Minus size={16} />
              </button>
            </>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={onAdd}
        className="btn-add"
      >
        <Plus size={16} /> {addLabel}
      </button>
    </div>
  );
};

export default ArrayInput;
