import request from '@/utils/request'
import type {
  PaginationParams,
  StudentSearchCriteria,
  SearchStudentByCriteriaResponse,
  CreateStudentResponse,
  DeleteStudentResponse,
  FindStudentByIdResponse,
  UpdateStudentResponse,
  Student,
  RegistrationParams,
  FindStudentByTeamIdResponse,
  DeactivateStudentResponse
} from './types'

enum API {
  STUDENTS_ENDPOINT = '/students',
  SEARCH_STUDENTS_ENDPOINT = '/students/search'
}

export const searchStudents = (params: PaginationParams, searchCriteria: StudentSearchCriteria) =>
  request.post<any, SearchStudentByCriteriaResponse>(API.SEARCH_STUDENTS_ENDPOINT, searchCriteria, {
    params
  })

export const findStudentById = (id: number) =>
  request.get<any, FindStudentByIdResponse>(`${API.STUDENTS_ENDPOINT}/${id}`)

// With a week, the server leaves out any teammate deactivated before it (BR-deactivated-student-weeks)
export const findStudentsByTeamId = (teamId: number, week?: string) =>
  request.get<any, FindStudentByTeamIdResponse>(`${API.STUDENTS_ENDPOINT}/teams/${teamId}`, {
    params: week ? { week } : undefined
  })

export const createStudent = (newStudent: Student, params: RegistrationParams) =>
  request.post<any, CreateStudentResponse>(API.STUDENTS_ENDPOINT, newStudent, { params })

export const updateStudent = (updatedStudent: Student) =>
  request.put<any, UpdateStudentResponse>(
    `${API.STUDENTS_ENDPOINT}/${updatedStudent.id}`,
    updatedStudent
  )

export const deactivateStudent = (id: number) =>
  request.post<any, DeactivateStudentResponse>(`${API.STUDENTS_ENDPOINT}/${id}/deactivate`)

export const deleteStudent = (id: number) =>
  request.delete<any, DeleteStudentResponse>(`${API.STUDENTS_ENDPOINT}/${id}`)
