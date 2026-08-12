# Smart Receipt Frontend

React 18 + Vite frontend for the Smart Receipt Spring Boot backend.

## Run

```bash
npm install
copy .env.example .env
npm run dev
```

Frontend: http://localhost:5173  
Backend: http://localhost:8080

Set `VITE_API_BASE_URL` in `.env` if the backend is hosted elsewhere.

## Implemented

- Landing page
- Register/login
- JWT Bearer authentication
- Protected routes
- Dashboard
- Receipt upload with JPG/JPEG/PNG/PDF validation
- OCR upload integration
- Receipt listing/details/edit/delete
- PDF download
- Profile
- Responsive dark SaaS UI
- Axios request/401 handling

## Expected backend endpoints

POST /api/auth/register
POST /api/auth/login
POST /api/receipts
GET /api/receipts
GET /api/receipts/{id}
PUT /api/receipts/{id}
DELETE /api/receipts/{id}
POST /api/receipts/upload
GET /api/receipts/{id}/pdf

The frontend reads the JWT from the login response using `token`, `accessToken`, or `jwt`. If your backend uses a different property name, change that line in `src/context/AuthContext.jsx`.

## Build

```bash
npm run build
```
