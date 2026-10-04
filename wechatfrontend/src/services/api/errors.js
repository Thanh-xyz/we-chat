const STATUS_MESSAGES = {
  400: 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại.',
  401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy dữ liệu được yêu cầu.',
  409: 'Dữ liệu đã tồn tại hoặc vừa được thay đổi.',
  423: 'Tài khoản đang tạm khóa. Vui lòng thử lại sau.',
  429: 'Bạn thao tác quá nhanh. Vui lòng chờ một chút.',
  500: 'Máy chủ đang gặp sự cố. Vui lòng thử lại.',
}

export class ApiError extends Error {
  constructor(message, { status = 0, validationErrors = null, code = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.validationErrors = validationErrors
    this.code = code
  }
}

export function normalizeApiError(error) {
  if (error instanceof ApiError) return error
  if (error?.code === 'ERR_CANCELED') {
    return new ApiError('Yêu cầu đã được hủy.', { code: 'REQUEST_CANCELED' })
  }

  const status = error?.response?.status ?? 0
  const body = error?.response?.data
  const safeServerMessage = typeof body?.message === 'string' ? body.message : null
  const message = safeServerMessage || STATUS_MESSAGES[status] || 'Không thể kết nối đến máy chủ.'
  return new ApiError(message, {
    status,
    validationErrors: body?.validationErrors ?? null,
    code: error?.code ?? null,
  })
}

export function errorMessage(error) {
  return normalizeApiError(error).message
}
