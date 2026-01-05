export const sanitizeDigits = (value: string) => value.replace(/\D/g, '');

export const sanitizeCardNumber = (value: string) => sanitizeDigits(value);

export const formatCardNumberInput = (value: string) => {
  const digits = sanitizeDigits(value).slice(0, 16);
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim();
};

export const formatExpiryInput = (value: string) => {
  const digits = sanitizeDigits(value).slice(0, 4);
  if (digits.length <= 2) {
    return digits;
  }
  return `${digits.slice(0, 2)}/${digits.slice(2)}`;
};

export const isValidLuhn = (value: string) => {
  const digits = sanitizeDigits(value);
  let sum = 0;
  let shouldDouble = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let digit = Number(digits[i]);
    if (Number.isNaN(digit)) {
      return false;
    }
    if (shouldDouble) {
      digit *= 2;
      if (digit > 9) {
        digit -= 9;
      }
    }
    sum += digit;
    shouldDouble = !shouldDouble;
  }
  return digits.length > 0 && sum % 10 === 0;
};

export type ExpiryValidationResult = { valid: boolean; reason?: 'format' | 'expired' };

export const validateExpiry = (value: string, now = new Date()): ExpiryValidationResult => {
  const digits = sanitizeDigits(value);
  if (digits.length < 4) {
    return { valid: false, reason: 'format' };
  }
  const month = Number(digits.slice(0, 2));
  const year = Number(digits.slice(2, 4));
  if (Number.isNaN(month) || Number.isNaN(year) || month < 1 || month > 12) {
    return { valid: false, reason: 'format' };
  }

  const fullYear = 2000 + year;
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  if (fullYear < currentYear || (fullYear === currentYear && month < currentMonth)) {
    return { valid: false, reason: 'expired' };
  }
  return { valid: true };
};
