import { Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import ProtectedRoute from "./components/ProtectedRoute";
import Home from "./pages/Home";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import UploadReceipt from "./pages/UploadReceipt";
import Receipts from "./pages/Receipts";
import ReceiptDetails from "./pages/ReceiptDetails";
import EditReceipt from "./pages/EditReceipt";
import Profile from "./pages/Profile";

export default function App(){
  return <Routes>
    <Route path="/" element={<Home/>}/>
    <Route path="/login" element={<Login/>}/>
    <Route path="/register" element={<Register/>}/>
    <Route element={<ProtectedRoute/>}>
      <Route element={<Layout/>}>
        <Route path="/dashboard" element={<Dashboard/>}/>
        <Route path="/upload" element={<UploadReceipt/>}/>
        <Route path="/receipts" element={<Receipts/>}/>
        <Route path="/receipts/:id" element={<ReceiptDetails/>}/>
        <Route path="/receipts/:id/edit" element={<EditReceipt/>}/>
        <Route path="/profile" element={<Profile/>}/>
      </Route>
    </Route>
    <Route path="*" element={<Home/>}/>
  </Routes>
}